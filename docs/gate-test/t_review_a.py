# -*- coding: utf-8 -*-
"""5.5 审核 TC-REVIEW-*（前半：状态机入口与单条审核；后半在 t_review_b.py）"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, login, load_state, load_tokens, record,
                     dump_results, save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
ID1, ID2, ID3 = P["GP-D1-001"]["id"], P["GP-D1-002"]["id"], P["GP-D1-003"]["id"]
ID4 = P["GP-D2-001"]["id"]


def main():
    b1 = TK["B1"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)

    # TC-REVIEW-01：GP-D1-002 过审（后续 TC-UPDATE-* 的前置）
    r = put(f"/api/gate-persons/{ID2}/approve", token=b1)
    record("TC-REVIEW-01", "B1", "200,msg:审批通过", r,
           data={"approveStatus": (r.data or {}).get("approveStatus")},
           pred=lambda: (r.data or {}).get("approveStatus") == "通过")

    # TC-REVIEW-02：已决记录不能再改判
    r = put(f"/api/gate-persons/{ID2}/approve", token=b1)
    record("TC-REVIEW-02", "B1", "400,msg:该人员当前状态不允许审核，请编辑后重新提交", r)

    # TC-REVIEW-03
    r = put(f"/api/gate-persons/{ID2}/reject", token=b1)
    record("TC-REVIEW-03", "B1", "400,msg:该人员当前状态不允许审核，请编辑后重新提交", r)

    # TC-REVIEW-05：范围外的单条审核
    r = put(f"/api/gate-persons/{ID4}/approve", token=b1)
    record("TC-REVIEW-05", "B1", "400,msg:人员不存在", r, note="跨部门按 ID 审核")

    # TC-REVIEW-13：USER 无 review
    r = put(f"/api/gate-persons/{ID1}/approve", token=TK["U1"])
    record("TC-REVIEW-13", "U1", "403", r)

    save_tokens(TK)
    dump_results("results_review_a.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
