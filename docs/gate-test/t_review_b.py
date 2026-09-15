# -*- coding: utf-8 -*-
"""5.5 审核 TC-REVIEW-*（后半：批量、上限、参数校验）"""
import os
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, login, load_state, load_tokens, record,
                     dump_results, save_tokens, save_state, redis_client)
from xls import build_gate_person_xlsx

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
ID1, ID2, ID3 = P["GP-D1-001"]["id"], P["GP-D1-002"]["id"], P["GP-D1-003"]["id"]
ID4 = P["GP-D2-001"]["id"]

REV_N = 200


def list_all(tok, page_size=100):
    out, page = [], 1
    while True:
        r = get(f"/api/gate-persons?page={page}&pageSize={page_size}", token=tok)
        if r.status != 200:
            break
        items = r.data["items"]
        out += items
        if len(out) >= r.data["total"] or not items:
            break
        page += 1
    return out


def main():
    b1, s1, a1 = TK["B1"], TK["S1"], TK["A1"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)

    # TC-REVIEW-04：驳回（强制重置同步状态）
    reason = urllib.parse.quote("证件不清晰")
    r = put(f"/api/gate-persons/{ID3}/reject?reason={reason}", token=b1)
    d = get(f"/api/gate-persons/{ID3}", token=s1).data
    record("TC-REVIEW-04", "B1", "200,msg:审批拒绝", r,
           data={"approveStatus": d["approveStatus"], "syncStatus": d["syncStatus"]},
           pred=lambda: d["approveStatus"] == "拒绝" and d["syncStatus"] == "未同步")

    # 为批量用例准备 200 条 D1 范围的待审核人员（走真实导入接口；已存在则跳过）
    rev = [p for p in list_all(b1) if p["code"].startswith("REV-")]
    if len(rev) < REV_N:
        rows = [("REV-%03d" % i, None, "批量测试%03d" % i, "1310000%04d" % i,
                 "35010219900201%04d" % i) for i in range(1, REV_N + 1)]
        r = post("/api/excel/gate-persons/import", token=b1,
                 files=[("file", "batch.xlsx", build_gate_person_xlsx(rows),
                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")])
        print("批量导入:", r.status, r.message, r.data)
        save_state("rev_import.json", r.data)
        rev = [p for p in list_all(b1) if p["code"].startswith("REV-")]
    else:
        print("REV- 行已存在，跳过导入")
    rev_ids = [p["id"] for p in rev]
    print("范围内 REV- 人数:", len(rev_ids))
    # 让本批重新回到待审核，便于重复执行
    pending = [p["id"] for p in rev if p["approveStatus"] == "审核中"]
    print("其中待审核:", len(pending))

    # TC-REVIEW-06：批量里混入范围外成员 → 整批回滚
    r = put("/api/gate-persons/reviews", token=b1,
            json_body={"ids": [ID1, ID4], "approved": True})
    d1 = get(f"/api/gate-persons/{ID1}", token=s1).data
    record("TC-REVIEW-06", "B1", "400,msg:人员不存在", r,
           data={"GP-D1-001 状态": d1["approveStatus"]},
           note="整批回滚：GP-D1-001 必须仍是审核中",
           pred=lambda: d1["approveStatus"] == "审核中")

    # TC-REVIEW-07：批量 200 条
    r = put("/api/gate-persons/reviews", token=b1,
            json_body={"ids": rev_ids[:REV_N], "approved": True})
    record("TC-REVIEW-07", "B1", "200", r, data=r.data,
           note="上限边界：恰好 200",
           pred=lambda: (r.data or {}).get("reviewed") == REV_N)

    # TC-REVIEW-08：201 条
    more = [p["id"] for p in list_all(b1) if p["code"].startswith("GP-D1-") or p["id"] == ID4]
    ids201 = rev_ids[:REV_N] + [ID1]
    r = put("/api/gate-persons/reviews", token=b1,
            json_body={"ids": ids201, "approved": True})
    record("TC-REVIEW-08", "B1", "400,msg:单次批量审核不能超过 200 人，请分批提交", r,
           note="本批 %d 条" % len(ids201))

    # TC-REVIEW-09/10/11
    r = put("/api/gate-persons/reviews", token=b1,
            json_body={"ids": [ID1, ID1], "approved": True})
    record("TC-REVIEW-09", "B1", "400,msg:审核记录的 ID 不能重复", r)

    r = put("/api/gate-persons/reviews", token=b1, json_body={"ids": [], "approved": True})
    record("TC-REVIEW-10", "B1", "400,msg:审核列表不能为空", r)

    r = put("/api/gate-persons/reviews", token=b1, json_body={"ids": [ID1]})
    record("TC-REVIEW-11", "B1", "400,msg:必须指定审核结论", r)

    # TC-REVIEW-12：全局角色可跨部门审核
    # 每轮新建一条 D2 的待审核人员，避免复用已被审过的记录（保证可重复执行）。
    import time
    from prep import mkface
    probe = "D2-REV-%d" % int(time.time())
    cr = post("/api/gate-persons", token=s1,
              form={"code": probe, "dept": D2N, "name": "跨部门审核探针",
                    "phone": "1310000%04d" % (int(time.time()) % 10000),
                    "idCard": "35010219900301%04d" % (int(time.time()) % 10000)},
              files=[("face", "p.png", mkface(), "image/png")])
    print("建 D2 探针:", cr.status, cr.message, (cr.data or {}).get("id"))
    if cr.data:
        r = put(f"/api/gate-persons/{cr.data['id']}/approve", token=a1)
        record("TC-REVIEW-12", "A1", "200,msg:审批通过", r,
               note="D2 的人，A1 未收窄 = ALL")
    else:
        record("TC-REVIEW-12", "A1", "201", cr, note="探针创建失败，无法执行")

    save_tokens(TK)
    dump_results("results_review_b.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
