# -*- coding: utf-8 -*-
"""5.9 人员进出记录 · 普通用户自助 TC-PR-*"""
import os
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, login, load_state, load_tokens, record, dump_results,
                     save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
q = urllib.parse.quote


def total(r):
    return (r.data or {}).get("total")


def main():
    b1 = TK["B1"]
    b2 = TK["B2"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)

    for tc, who, key, want, note in [
        ("TC-PR-01", "U1", "U1", 2, "SELF：linked_user_id=U1 的 2 条；看不到余义"),
        ("TC-PR-02", "U2", "U2", 1, "只看得到自己的 1 条"),
        ("TC-PR-03", "U3", "U3", 0, "SELF 全空 = 默认拒绝，不是错误"),
        ("TC-PR-05", "B1", "B1", 4, "DEPARTMENTS：D1 的 4 条全部命中"),
        ("TC-PR-06", "B2", "B2", 0, "D2 下无记录"),
    ]:
        r = get("/api/person-records?pageSize=100", token=TK[key])
        record(tc, who, "200", r, data={"total": total(r)}, note=note,
               pred=lambda t=total(r), w=want: t == w)

    # TC-PR-04：先在范围内裁剪，再按关键词过滤
    r = get("/api/person-records?pageSize=100&keyword=" + q("余义"), token=TK["U1"])
    record("TC-PR-04", "U1", "200", r, data={"total": total(r)},
           note="关键词命中范围外数据时也必须为 0（先裁范围再过滤）",
           pred=lambda: total(r) == 0)

    # TC-PR-07：人员进出「导出」在后端不存在
    r = get("/api/excel/person-records/export", token=TK["U1"])
    record("TC-PR-07", "U1", "403", r, data={"U1": r.status},
           note="见 G5：U1 无 person-record:export，被权限拦下", info=True)
    r2 = get("/api/excel/person-records/export", token=TK["B1"])
    record("TC-PR-07b", "B1", "200", r2,
           data={"B1": r2.status, "message": r2.message},
           note="带导出权限的账号访问该路径的真实响应（判定该资源到底存不存在）",
           info=True)

    # TC-PR-08
    r = get("/api/person-records?pageSize=101", token=TK["U1"])
    record("TC-PR-08", "U1", "400,msg:每页数量必须在 1 到 100 之间", r)

    save_tokens(TK)
    dump_results("results_pr.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
