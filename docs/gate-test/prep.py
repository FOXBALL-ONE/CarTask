# -*- coding: utf-8 -*-
"""阶段 0：环境自检 + 测试账号/部门准备。

产出 state.json（部门、账号 id）与 tokens.json（各角色令牌）。
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (BASE, get, post, put, delete, login, sms_login, session,
                     load_state, save_state, save_tokens, request)

ST = {}


def log(*a):
    print(*a, flush=True)


def mkface():
    """一个魔数合法的最小 PNG（后端只校验魔数，不解析像素）。"""
    return (b"\x89PNG\r\n\x1a\n"
            + b"\x00\x00\x00\rIHDR" + os.urandom(60)
            + b"\x00\x00\x00\x00IEND\xaeB`\x82")


def main():
    # ── 1. 超级管理员登录 ──────────────────────────────────────────────
    s1, r = login("admin", "admin")
    if not s1:
        log("admin 登录失败:", r.status, r.raw[:300])
        return 1
    log("admin 登录成功")

    # ── 2. 部门：D1 已存在，D2 按需创建 ────────────────────────────────
    depts = {d["department_code"]: d for d in get("/api/departments", token=s1).data["departments"]}
    if "GATE-D2" not in depts:
        r = post(f"/api/departments?name=%E9%97%A8%E7%A6%81%E6%B5%8B%E8%AF%95%E9%83%A8%E9%97%A8"
                 f"&department_code=GATE-D2&sort_order=99", token=s1)
        log("创建 D2:", r.status, r.message)
        depts = {d["department_code"]: d
                 for d in get("/api/departments", token=s1).data["departments"]}
    D1 = depts["AUTO-05800DE49C"]["id"]
    D2 = depts["GATE-D2"]["id"]
    log(f"D1={D1} ({depts['AUTO-05800DE49C']['name']})  D2={D2} ({depts['GATE-D2']['name']})")

    # ── 3. 测试账号 ────────────────────────────────────────────────────
    want = {
        "A1": ("t_admin", "门禁测试-平台管理", "13000000001", D1, 2),
        "B1": ("t_dept_d1", "门禁测试-部门管理D1", "13000000002", D1, 3),
        "B2": ("t_dept_d2", "门禁测试-部门管理D2", "13000000003", D2, 3),
        "U3": ("t_user_none", "门禁测试-普通无归属", "13000000099", D1, 4),
    }
    ids = {}
    users = {u["username"]: u for u in _all_users(s1)}
    for key, (un, name, phone, dept, role) in want.items():
        if un in users:
            ids[key] = users[un]["id"]
            log(f"{key} 已存在 id={ids[key]}")
            continue
        r = post("/api/users", token=s1, json_body={
            "username": un, "password": "Test@2026", "name": name,
            "deptId": dept, "phone": phone, "roleIds": [role], "status": 1})
        log(f"创建 {key} {un}: {r.status} {r.message}")
        if r.data:
            ids[key] = r.data["id"]
    if len(ids) != 4:
        log("账号准备不完整:", ids)
        return 1

    # ── 4. 部门管理范围 ────────────────────────────────────────────────
    for key, dept in (("B1", D1), ("B2", D2)):
        r = put(f"/api/users/{ids[key]}/managed-departments", token=s1,
                json_body={"departments": [{"department_id": dept, "include_descendants": False}]})
        log(f"{key} 分配范围 D{dept}: {r.status} {r.message}")

    ST.update({"D1": D1, "D2": D2, "D1_CODE": "AUTO-05800DE49C",
               "D1_NAME": "同步车主", "D2_CODE": "GATE-D2", "D2_NAME": "门禁测试部门",
               "ids": ids})

    # ── 5. 首登改密 + 拿令牌 ───────────────────────────────────────────
    tokens = {"S1": s1}
    pw1 = "Test@2026"
    pw2 = "Test@2026x"
    for key in ("A1", "B1", "B2", "U3"):
        tok, r = login(want[key][0], pw1)
        if tok:
            r2 = put("/api/profile/password", token=tok, json_body={
                "current_password": pw1, "new_password": pw2})
            log(f"{key} 改密: {r2.status} {r2.message}")
        tok, r = login(want[key][0], pw2)
        log(f"{key} 用新密码登录: {'OK' if tok else (r.status, r.message)}")
        tokens[key] = tok

    # ── 6. U1 / U2 短信登录 + 改密 ─────────────────────────────────────
    uids = []
    for key, phone in (("U1", "18950315520"), ("U2", "13705023122")):
        tok, r = sms_login(phone)
        if tok:
            r2 = put("/api/profile/password", token=tok, json_body={
                "current_password": "Fqjg20221022", "new_password": pw2})
            log(f"{key} 改密: {r2.status} {r2.message}")
        tok, r = sms_login(phone)
        log(f"{key} 重登: {'OK' if tok else (r.status, r.message)}")
        tokens[key] = tok
        st = session(tok) if tok else None
        uids.append((key, phone, st.data["user_id"] if st and st.data else None))

    save_state("state.json", ST)
    save_tokens(tokens)
    log("tokens:", {k: ("有" if v else "无") for k, v in tokens.items()})
    log("U1/U2 user_id:", uids)
    return 0


def _all_users(s1, keyword=None):
    """扫页取全部用户（接口分页上限 100）。"""
    out, page = [], 1
    while True:
        q = f"/api/users?page={page}&pageSize=100"
        if keyword:
            q += "&keyword=" + keyword
        r = get(q, token=s1)
        if not r.data:
            break
        items = r.data.get("items", [])
        out += items
        if len(out) >= r.data.get("total", 0) or not items:
            break
        page += 1
    return out


if __name__ == "__main__":
    sys.exit(main())
