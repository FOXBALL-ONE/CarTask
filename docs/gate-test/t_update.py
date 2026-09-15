# -*- coding: utf-8 -*-
"""5.4 编辑与状态回退 TC-UPDATE-*"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, login, load_state, load_tokens, record,
                     dump_results, save_tokens, save_state, redis_client)
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
ID1, ID2 = P["GP-D1-001"]["id"], P["GP-D1-002"]["id"]
ID4, ORPHAN, BACKFILL = P["GP-D2-001"]["id"], 5, 10


def upd(tok, pid, **fields):
    face = fields.pop("_face", None)
    files = [("face", "new.png", face, "image/png")] if face else None
    return put(f"/api/gate-persons/{pid}", token=tok, form=fields, files=files)


def current(tok, pid):
    return get(f"/api/gate-persons/{pid}", token=tok).data


def main():
    b1, s1 = TK["B1"], TK["S1"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)

    before = current(s1, ID2)
    print("GP-D1-002 起始:", before["name"], before["approveStatus"])

    # TC-UPDATE-01：改名字 → 打回待审核（可重复运行：在两个名字间来回切）
    target = "门禁测试甲2" if before["name"] != "门禁测试甲2" else "门禁测试甲"
    r = upd(b1, ID2, name=target)
    after = current(s1, ID2)
    record("TC-UPDATE-01", "B1", "200", r,
           data={"approveStatus": after["approveStatus"], "syncStatus": after["syncStatus"]},
           note="防「先过审再改」绕过审核",
           pred=lambda: after["approveStatus"] == "审核中" and after["syncStatus"] == "未同步")

    # 复位成已通过，供 TC-UPDATE-02 观察「原样提交不应打回」
    put(f"/api/gate-persons/{ID2}/approve", token=b1)
    cur = current(s1, ID2)
    print("复审后:", cur["approveStatus"])

    # TC-UPDATE-02：原样回填全部字段（不传 face）→ 状态不变、不写更新审计
    r = upd(b1, ID2, code=cur["code"], dept=cur["dept"], name=cur["name"],
            phone=cur["phone"], idCard=cur["idCard"])
    after2 = current(s1, ID2)
    record("TC-UPDATE-02", "B1", "200", r,
           data={"before": cur["approveStatus"], "after": after2["approveStatus"]},
           note="「无变化不算改动」；审计由 TC-AUDIT-07 核对",
           pred=lambda: after2["approveStatus"] == cur["approveStatus"] == "通过")

    # TC-UPDATE-03：只回填 dept（department_code 从 NULL 回填为编码）
    # 用直连 SQL 造的 GP-BACKFILL：dept 文本已经是「同步车主」，只有 department_code 是 NULL。
    # 这正是规则 4 想覆盖的形态——文本没变、只有编码被补齐，不应算「用户可见内容变化」。
    o_before = current(s1, BACKFILL)
    r = upd(s1, BACKFILL, dept=D1N)
    o_after = current(s1, BACKFILL)
    record("TC-UPDATE-03", "S1", "200", r,
           data={"before": o_before["approveStatus"], "after": o_after["approveStatus"]},
           note="编码回填不是用户可见内容，不应退回待审核",
           pred=lambda: o_after["approveStatus"] == o_before["approveStatus"] == "通过")

    # TC-UPDATE-04：改范围外人员
    r = upd(b1, ID4, name="越界改名")
    record("TC-UPDATE-04", "B1", "400,msg:人员不存在", r, note="编辑入口先做范围校验")

    # TC-UPDATE-05：把数据挪出范围
    r = upd(b1, ID1, dept=D2N)
    record("TC-UPDATE-05", "B1", "403,msg:无权在其它部门下操作数据", r)

    # TC-UPDATE-06：编号与他人重复
    r = upd(b1, ID1, code="GP-D1-002")
    record("TC-UPDATE-06", "B1", "400,msg:人员编号已存在", r)

    # TC-UPDATE-07：越界改部门 + 同时改姓名 → 整条回滚，姓名也不能变
    name_before = current(s1, ID1)["name"]
    r = upd(b1, ID1, dept=D2N, name="不应被改掉的名字")
    name_after = current(s1, ID1)["name"]
    record("TC-UPDATE-07", "B1", "403", r,
           data={"name_before": name_before, "name_after": name_after},
           note="方法级事务：抛异常整条回滚",
           pred=lambda: name_after == name_before)

    # TC-UPDATE-08：换人脸
    face_before = current(s1, ID1)["face"]
    r = upd(b1, ID1, _face=mkface())
    after8 = current(s1, ID1)
    record("TC-UPDATE-08", "B1", "200", r,
           data={"face_changed": after8["face"] != face_before,
                 "approveStatus": after8["approveStatus"]},
           note="旧文件按新 code/部门重新挂载（relinkBusiness）",
           pred=lambda: after8["face"] != face_before and after8["approveStatus"] == "审核中")
    save_state("face_ids.json", dict(load_state("face_ids.json", {}),
                                     **{"GP-D1-001": after8["face"].split("/files/")[1].split("/")[0]}))

    # TC-UPDATE-09：超管不受限
    r = upd(s1, ID1, name="章规希")
    record("TC-UPDATE-09", "S1", "200", r)

    # TC-UPDATE-10：USER 无 manage
    r = upd(TK["U3"], ID1, name="越权")
    record("TC-UPDATE-10", "U3", "403", r)

    save_tokens(TK)
    dump_results("results_update.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
