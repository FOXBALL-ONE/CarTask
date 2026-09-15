# -*- coding: utf-8 -*-
"""5.2 列表与详情 · 范围可见性 TC-LIST-*"""
import os
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, login, load_state, load_tokens, record, dump_results,
                     save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
D2_ID = P["GP-D2-001"]["id"]

q = urllib.parse.quote


def codes(r):
    return sorted(i["code"] for i in (r.data or {}).get("items", []))


def total(r):
    return (r.data or {}).get("total")


def qs(**kw):
    parts = [f"{k}={q(str(v))}" for k, v in kw.items()]
    return "/api/gate-persons?" + "&".join(parts)


def main():
    # A2 = t_admin 的另一条会话，收窄到 D1（直写 Redis 绕过 F1）
    a2tok, _ = login("t_admin", "Test@2026x")
    rd = redis_client()
    rd.set_working_department(a2tok, D1)
    TK["A2"] = a2tok
    print("A2 session:", session_scope(a2tok))

    for tc, who, tok, exp_total, exp_codes, note in [
        ("TC-LIST-01", "S1", "S1", 5, ["GP-D1-001", "GP-D1-002", "GP-D1-003",
                                       "GP-D2-001", "GP-ORPHAN"], "ALL 不过滤"),
        ("TC-LIST-02", "A1(未切部门)", "A1", 5, ["GP-D1-001", "GP-D1-002", "GP-D1-003",
                                                 "GP-D2-001", "GP-ORPHAN"], "ALL"),
        ("TC-LIST-03", "A2(切 D1)", "A2", 3, ["GP-D1-001", "GP-D1-002", "GP-D1-003"],
         "重点：GP-ORPHAN 是否泄露；实测若为 4 即缺陷"),
        ("TC-LIST-04", "B1(D1)", "B1", 3, ["GP-D1-001", "GP-D1-002", "GP-D1-003"],
         "GP-ORPHAN 因 department_code=NULL 且 dept 不可解析 → 不可见"),
        ("TC-LIST-05", "B2(D2)", "B2", 1, ["GP-D2-001"], "跨部门互不可见"),
    ]:
        r = get(qs(pageSize=100), token=TK[tok])
        record(tc, who, "200", r, data={"total": total(r), "codes": codes(r)}, note=note,
               pred=lambda t=total(r), c=codes(r), et=exp_total, ec=exp_codes:
               t == et and c == ec)

    # TC-LIST-06：USER 无 gate-person:read
    for who in ("U1", "U2", "U3"):
        r = get(qs(pageSize=100), token=TK[who])
        record("TC-LIST-06", who, "403", r, note="USER 角色无 gate-person:read")

    # TC-LIST-07：跨部门值域过滤先裁范围
    r = get(qs(pageSize=100, dept=D2N), token=TK["B1"])
    record("TC-LIST-07", "B1", "200", r, data={"total": total(r)}, note=f"dept={D2N}",
           pred=lambda: total(r) == 0)

    # TC-LIST-08：审核状态筛选，两种写法等价
    r1 = get(qs(pageSize=100, approveStatus="审核中"), token=TK["B1"])
    r2 = get(qs(pageSize=100, approveStatus="PENDING"), token=TK["B1"])
    record("TC-LIST-08", "B1", "200", r1,
           data={"中文": total(r1), "英文": total(r2)},
           note="两种写法应等价且等于范围内 PENDING 条数",
           pred=lambda: total(r1) == total(r2) == 3)

    # TC-LIST-09：G1 —— 没有任何接口能把状态置为已同步
    r = get(qs(pageSize=100, syncStatus="已同步"), token=TK["B1"])
    record("TC-LIST-09", "B1", "200", r, data={"total": total(r)},
           note="见 G1：应为 0，且全库都取不到「已同步」",
           pred=lambda: total(r) == 0)

    # TC-LIST-10
    r = get(qs(pageSize=101), token=TK["B1"])
    record("TC-LIST-10", "B1", "400,msg:每页数量必须在 1 到 100 之间", r)

    # TC-LIST-11 / 12：范围外 vs 不存在，响应必须完全一致
    r11 = get(f"/api/gate-persons/{D2_ID}", token=TK["B1"])
    record("TC-LIST-11", "B1", "400,msg:人员不存在", r11, note=f"范围外 id={D2_ID}")
    r12 = get("/api/gate-persons/99999999", token=TK["B1"])
    record("TC-LIST-12", "B1", "400,msg:人员不存在", r12, note="不存在的 id")
    record("TC-LIST-11=12", "B1", "400", r12,
           data={"out_of_scope": [r11.status, r11.message],
                 "not_exists": [r12.status, r12.message]},
           note="两类响应体必须逐字一致",
           pred=lambda: r11.raw == r12.raw)

    # TC-LIST-13：内存分页
    r = get(qs(pageSize=2, page=2), token=TK["S1"])
    record("TC-LIST-13", "S1", "200", r,
           data={"page2_items": len((r.data or {}).get("items", [])), "total": total(r)},
           note="total 应与全量一致",
           pred=lambda: len(r.data["items"]) <= 2 and total(r) == 5)

    save_tokens(TK)
    dump_results("results_list.json")
    return 0


def session_scope(tok):
    from harness import session
    d = session(tok).data
    return (d.get("scope"), d.get("working_department_id"))


if __name__ == "__main__":
    sys.exit(main())
