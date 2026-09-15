# -*- coding: utf-8 -*-
"""5.11 门禁授权 access_control TC-AC-*（邻接模块，无前端页）

注意：POST /api/access-controls 会先落库、再在写响应时因懒加载代理序列化失败返回 500，
所以新建后必须回到列表里取 id，不能依赖创建响应。
"""
import os
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, delete, login, load_state, load_tokens,
                     record, dump_results, save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
EPOCH = int(time.time())
q = urllib.parse.quote


def mk(tok, name, dept_id, number):
    return post("/api/access-controls", token=tok, json_body={
        "name": name, "phone": "14700000001", "personNumber": number,
        "accessControlList": "测试门禁点", "department": {"id": dept_id}})


def all_items(tok, token_key="B1"):
    r = get("/api/access-controls?page=1&pageSize=100", token=tok)
    return (r.data or {}).get("content") or (r.data or {}).get("items") or [], r


def find_id(tok, number):
    items, _ = all_items(tok)
    for i in items:
        if i.get("personNumber") == number:
            return i.get("id")
    return None


def main():
    b1, s1 = TK["B1"], TK["S1"]

    # 列表本身是否可读
    items0, r0 = all_items(b1)
    print("现网 access_control 条数:", len(items0))

    # TC-AC-01：新建（记录 500 现象 + 是否落库）
    n1, n2 = "AC-D1-%d" % EPOCH, "AC-D2-%d" % EPOCH
    r = mk(b1, "门禁授权D1", D1, n1)
    ac_d1 = find_id(s1, n1)
    record("TC-AC-01", "B1", "", r,
           data={"http": r.status, "message": r.message, "落库后的 id": ac_d1},
           note="接口返回 500，但记录已落库（先提交事务、再序列化响应时炸）",
           info=True)

    r = mk(s1, "门禁授权D2", D2, n2)
    ac_d2 = find_id(s1, n2)
    print("ac_d1=%s ac_d2=%s" % (ac_d1, ac_d2))

    # TC-AC-05：审核原因为空
    r = post(f"/api/access-controls/{ac_d1}/review?approved=true&review_reason=", token=b1)
    record("TC-AC-05", "B1", "400,msg:审核原因不能为空", r)

    # TC-AC-04：正常审核
    r = post(f"/api/access-controls/{ac_d1}/review?approved=true&review_reason="
             + q("资料齐全"), token=b1)
    record("TC-AC-04", "B1", "", r, data={"http": r.status, "message": r.message},
           note="审核是否能正常返回（同样可能撞上序列化问题）", info=True)

    # TC-AC-02：同步接口未接入
    r = post(f"/api/access-controls/{ac_d1}/sync", token=b1)
    record("TC-AC-02", "B1", "", r, data={"http": r.status, "message": r.message},
           note="方案预期 500（IllegalStateException 无专用处理器）", info=True)

    # TC-AC-03：删除对所有角色 403
    r = delete(f"/api/access-controls/{ac_d1}", token=b1)
    record("TC-AC-03", "B1", "403", r, note="denyAll()")
    r = delete(f"/api/access-controls/{ac_d1}", token=s1)
    record("TC-AC-03b", "S1", "403", r, note="denyAll()")

    # TC-AC-06：跨部门
    r = get(f"/api/access-controls/{ac_d2}", token=b1)
    record("TC-AC-06a", "B1", "", r,
           data={"http": r.status, "name": (r.data or {}).get("name")},
           note="方案预期 400 记录不存在", info=True)

    items, r = all_items(b1)
    names = [i.get("name") for i in items]
    record("TC-AC-06b", "B1", "200", r,
           data={"total": (r.data or {}).get("total"),
                 "列表含 D2 的授权": any("D2" in str(n) for n in names)},
           note="列表是否按部门范围裁剪（B1 只应看到 D1）", info=True)

    r = post(f"/api/access-controls/{ac_d2}/review?approved=true&review_reason="
             + q("越权审核"), token=b1)
    record("TC-AC-06c", "B1", "", r, data={"http": r.status, "message": r.message},
           note="B1(D1) 审核 D2 的授权：方案预期 400 记录不存在", info=True)

    save_tokens(TK)
    dump_results("results_ac.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
