# -*- coding: utf-8 -*-
"""5.1 登录闸门与工作部门 TC-AUTH-*"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, post, put, login, session, load_state, load_tokens,
                     record, dump_results, save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]


def chk(tc, actor, expect, r, keys=None, note="", pred=None):
    data = None
    if keys and isinstance(r.data, dict):
        data = {k: r.data.get(k) for k in keys}
    elif r.data is not None:
        data = r.data if isinstance(r.data, (int, str, list)) else {"_keys": list(r.data)}
    return record(tc, actor, expect, r, data=data, note=note, pred=pred)


SESS = ("role", "scope", "working_department_id", "working_department_name")


def main():
    s1 = TK["S1"]

    r = session(s1)
    opts = sorted(o["id"] for o in (r.data or {}).get("working_department_options", []))
    chk("TC-AUTH-01", "S1", "200,msg:操作成功", r, SESS,
        note=f"options={opts}（期望含 {D1},{D2}）",
        pred=lambda: r.data.get("role") == "SUPER_ADMIN"
        and r.data.get("working_department_id") is None
        and r.data.get("scope") == "ALL"
        and D1 in opts and D2 in opts)

    r = session(TK["A1"])
    chk("TC-AUTH-02", "A1", "200", r, SESS,
        pred=lambda: r.data.get("working_department_id") is None
        and r.data.get("scope") == "ALL")

    r = session(TK["B1"])
    chk("TC-AUTH-03", "B1", "200", r, SESS,
        note=f"期望默认落在 D1={D1}",
        pred=lambda: r.data.get("working_department_id") == D1
        and r.data.get("scope") == "DEPARTMENT")

    r = put(f"/api/auth/working-department?department_id={D2}", token=TK["B1"])
    chk("TC-AUTH-04", "B1", "403,msg:无权切换到该部门", r)

    r = put("/api/auth/working-department", token=TK["B1"])
    chk("TC-AUTH-05", "B1", "403,msg:必须选择一个工作部门", r)

    r = put(f"/api/auth/working-department?department_id={D1}", token=TK["A1"])
    chk("TC-AUTH-06", "A1", "200,msg:工作部门已切换", r, SESS)
    r2 = session(TK["A1"])
    record("TC-AUTH-06b", "A1", "200", r2, data={k: r2.data.get(k) for k in SESS},
           note="切 D1 后会话应收窄为 DEPARTMENT —— 实测未生效，见 F1",
           pred=lambda: r2.data.get("scope") == "DEPARTMENT"
           and r2.data.get("working_department_id") == D1)

    # F1 缺陷导致上面的切换写不进 Redis；这里直连 Redis 写入同样的字段，
    # 让后续依赖「A2 = 收窄到 D1」的用例仍可执行。
    rd = redis_client()
    rd.set_working_department(TK["A1"], D1)
    r3 = session(TK["A1"])
    record("TC-AUTH-06c", "A1", "200", r3,
           data={k: r3.data.get(k) for k in SESS},
           note="用 Redis 直写会话绕过 F1，验证收窄后的语义确实生效",
           pred=lambda: r3.data.get("scope") == "DEPARTMENT"
           and r3.data.get("working_department_id") == D1)

    r = put("/api/auth/working-department", token=TK["U1"])
    chk("TC-AUTH-07", "U1", "403,msg:普通用户没有工作部门", r)

    # TC-AUTH-08 需要一个 must_change_password 仍为 true 的账号
    tmp = "t_first_login"
    got = get("/api/users?keyword=" + tmp, token=s1).data or {}
    if not any(u["username"] == tmp for u in got.get("items", [])):
        cr = post("/api/users", token=s1, json_body={
            "username": tmp, "password": "Test@2026", "name": "首登未改密",
            "deptId": D1, "phone": "13000000098", "roleIds": [3], "status": 1})
        print("建首登账号:", cr.status, cr.message)
    t_tmp, _ = login(tmp, "Test@2026")
    r = get("/api/gate-persons", token=t_tmp)
    chk("TC-AUTH-08", tmp, "403,msg:首次登录必须先修改密码", r,
        note="未改密账号访问业务接口")
    TK["FIRST_LOGIN"] = t_tmp

    r = get("/api/gate-persons", token=TK["U1"])
    chk("TC-AUTH-09", "U1", "403,notmsg:首次登录必须先修改密码", r,
        note="已改密但仍无 gate-person:read")

    r = put("/api/auth/working-department?department_id=99999", token=s1)
    chk("TC-AUTH-10", "S1", "400,msg:部门不存在", r)

    # 复位 A1 为全局（后续 TC-LIST-02 / TC-CREATE-02 需要 A1 = ALL）
    r = put("/api/auth/working-department", token=TK["A1"])
    print("A1 复位为 ALL（API）:", r.status, r.message)
    rd.set_working_department(TK["A1"], None)
    r = session(TK["A1"])
    print("A1 复位后会话:", r.data.get("scope"), r.data.get("working_department_id"))

    save_tokens(TK)
    dump_results("results_auth.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
