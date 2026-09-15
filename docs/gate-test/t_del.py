# -*- coding: utf-8 -*-
"""5.6 删除申请与审批 TC-DEL-*"""
import os
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, delete, login, load_state, load_tokens,
                     record, dump_results, save_tokens, save_state, redis_client)
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
D2_ID = P["GP-D2-001"]["id"]

EPOCH = int(time.time())
TS = EPOCH % 10000          # 身份证后 4 位
PH = EPOCH % 100000000      # 手机号后 8 位
REASONS = {}


def mkperson(tok, code, dept, name, phone, idc):
    r = post("/api/gate-persons", token=tok,
             form={"code": code, "dept": dept, "name": name,
                   "phone": phone, "idCard": idc},
             files=[("face", "p.png", mkface(), "image/png")])
    return (r.data or {}).get("id")


def mkreq(tok, pid, reason):
    return post(f"/api/gate-persons/{pid}/delete-requests", token=tok,
                json_body={"reason": reason})


def listreq(tok, **kw):
    qs = "&".join(f"{k}={v}" for k, v in kw.items())
    return get("/api/gate-persons/delete-requests" + ("?" + qs if qs else ""), token=tok)


def main():
    b1, b2, s1 = TK["B1"], TK["B2"], TK["S1"]

    # 专用探针人员（每轮新建，保证可重复执行）
    p_del1 = mkperson(b1, "DEL-A-%d" % EPOCH, D1N, "待删甲", "132%08d" % PH,
                      "35010219900401%04d" % TS)
    p_del2 = mkperson(b1, "DEL-B-%d" % EPOCH, D1N, "待删乙", "133%08d" % PH,
                      "35010219900402%04d" % TS)
    p_del12 = mkperson(s1, "DEL-C-%d" % EPOCH, D2N, "待删丙(D2)", "134%08d" % PH,
                       "35010219900403%04d" % TS)
    p_del13 = mkperson(b1, "DEL-D-%d" % EPOCH, D1N, "待删丁", "135%08d" % PH,
                       "35010219900404%04d" % TS)
    print("探针:", p_del1, p_del2, p_del12, p_del13)
    if not all([p_del1, p_del2, p_del12, p_del13]):
        print("探针创建失败，终止")
        return 1

    # TC-DEL-01/02：物理删除接口对所有角色 403
    r = delete(f"/api/gate-persons/{p_del1}", token=b1)
    record("TC-DEL-01", "B1", "403", r, note="@PreAuthorize(denyAll())")
    r = delete(f"/api/gate-persons/{p_del1}", token=s1)
    record("TC-DEL-02", "S1", "403", r, note="确认没有「超管后门」")

    # TC-DEL-04：原因为空
    r = mkreq(b1, p_del1, "   ")
    record("TC-DEL-04", "B1", "400,msg:删除原因不能为空", r)

    # TC-DEL-05：对范围外人员提申请
    r = mkreq(b1, p_del12, "越界")
    record("TC-DEL-05", "B1", "400,msg:人员不存在", r)

    # TC-DEL-15：USER 无 manage
    r = mkreq(TK["U3"], p_del1, "越权")
    record("TC-DEL-15", "U3", "403", r)

    # TC-DEL-03：正常提交
    r1 = mkreq(b1, p_del1, "离职")
    REASONS["del1"] = (r1.data or {}).get("id")
    record("TC-DEL-03", "B1", "201", r1,
           data={"status": (r1.data or {}).get("status")},
           pred=lambda: (r1.data or {}).get("status") == "待处理")

    # D1 / D2 各再准备一条申请，用于列表裁剪与跨部门审批
    r2 = mkreq(b1, p_del2, "转岗")
    REASONS["del2"] = (r2.data or {}).get("id")
    r3 = mkreq(s1, p_del12, "D2 离职")
    REASONS["del12"] = (r3.data or {}).get("id")
    r13 = mkreq(b1, p_del13, "调离")
    REASONS["del13"] = (r13.data or {}).get("id")
    print("申请单:", REASONS)

    # TC-DEL-06：B1 的列表只含 D1 的申请
    r = listreq(b1, pageSize=100)
    ids = [i["id"] for i in (r.data or {}).get("items", [])]
    record("TC-DEL-06", "B1", "200", r, data={"total": (r.data or {}).get("total")},
           note="应含 %s，不含 D2 的 %s" % (REASONS["del1"], REASONS["del12"]),
           pred=lambda: REASONS["del1"] in ids and REASONS["del12"] not in ids)

    # TC-DEL-07：B2 看不到 D1 的申请（含手机号/身份证快照）
    r = listreq(b2, pageSize=100)
    ids2 = [i["id"] for i in (r.data or {}).get("items", [])]
    record("TC-DEL-07", "B2", "200", r, data={"total": (r.data or {}).get("total")},
           note="不得含 D1 的 %s" % REASONS["del1"],
           pred=lambda: REASONS["del1"] not in ids2)

    # TC-DEL-08：USER
    r = listreq(TK["U1"], pageSize=100)
    record("TC-DEL-08", "U1", "403", r)

    # TC-DEL-12/14：跨部门审批
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del12']}/approve", token=b1)
    record("TC-DEL-12", "B1", "400,msg:删除申请不存在", r, note="D2 的申请单")
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del1']}/approve", token=b2)
    record("TC-DEL-14", "B2", "400,msg:删除申请不存在", r, note="D1 的申请单")

    # TC-DEL-13：先提申请，再把人员挪出范围，然后审批
    #   S1 把 p_del13 的 dept 改到 D2（全局权限）
    r = put(f"/api/gate-persons/{p_del13}", token=s1,
            form={"dept": D2N})
    print("S1 把 %s 挪到 D2: %s %s" % (p_del13, r.status, r.message))
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del13']}/approve", token=b1)
    lr = listreq(s1, pageSize=100)
    cur = [i for i in (lr.data or {}).get("items", []) if i["id"] == REASONS["del13"]]
    record("TC-DEL-13", "B1", "400,msg:人员不存在", r,
           data={"申请单状态": cur[0]["status"] if cur else None},
           note="申请单可见性不构成删除授权；状态须保持待处理",
           pred=lambda: cur and cur[0]["status"] == "待处理")

    # TC-DEL-11：驳回，人员仍在
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del2']}/reject", token=b1)
    still = get(f"/api/gate-persons/{p_del2}", token=s1)
    record("TC-DEL-11", "B1", "200", r, data={"人员仍在": still.status == 200},
           note="驳回不删人", pred=lambda: still.status == 200)

    # TC-DEL-09：同意 → 人员物理删除
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del1']}/approve", token=b1)
    gone = get(f"/api/gate-persons/{p_del1}", token=s1)
    record("TC-DEL-09", "B1", "200,msg:已同意删除申请", r,
           data={"删除后再取人员": gone.status, "message": gone.message},
           note="人员须物理删除（再取为 400 人员不存在）",
           pred=lambda: gone.status == 400)

    # TC-DEL-10：重复审批
    r = put(f"/api/gate-persons/delete-requests/{REASONS['del1']}/approve", token=b1)
    record("TC-DEL-10", "B1", "400,msg:删除申请已处理", r)

    save_state("del_requests.json", {"REASONS": REASONS, "persons": {
        "p_del1": p_del1, "p_del2": p_del2, "p_del12": p_del12, "p_del13": p_del13}})
    save_tokens(TK)
    dump_results("results_del.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
