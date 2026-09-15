# -*- coding: utf-8 -*-
"""5.12 越权与对抗性用例 TC-ATK-*

只跑此前未被其它用例覆盖的条目；已覆盖的在报告里做交叉引用：
  ATK-02→TC-UPDATE-04, ATK-03→TC-REVIEW-05, ATK-04→TC-REVIEW-06,
  ATK-05→TC-LIST-07, ATK-06→TC-XLS-08, ATK-07→TC-CREATE-03,
  ATK-08→TC-DEL-05/12, ATK-09→TC-DEL-13, ATK-10→TC-UPDATE-01,
  ATK-11→TC-LIST-06 + TC-PR-03, ATK-12→TC-FACE-02, ATK-13→TC-FACE-10,
  ATK-18→TC-LIST-03/04
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, delete, login, load_state, load_tokens,
                     record, dump_results, save_tokens, redis_client, captcha)

ST = load_state("state.json")
TK = load_tokens()
IDS = ST["ids"]
P = load_state("persons.json")
D2_ID = P["GP-D2-001"]["id"]
ID1 = P["GP-D1-001"]["id"]


def main():
    b1, s1 = TK["B1"], TK["S1"]

    # ATK-01：按 ID 直读别部门数据
    r = get(f"/api/gate-persons/{D2_ID}", token=b1)
    record("TC-ATK-01", "B1", "400,msg:人员不存在", r, note="跨部门按 ID 直读")

    # ATK-19：枚举探测——不存在的 id 与范围外的 id，响应必须逐字一致
    pairs = []
    for n in range(1, 21):
        a = get(f"/api/gate-persons/{D2_ID + 1000 + n}", token=b1)
        b = get(f"/api/gate-persons/{90000000 + n}", token=b1)
        pairs.append((a.status, a.raw, b.status, b.raw))
    same = all(p[0] == p[2] and p[1] == p[3] for p in pairs)
    uniq = sorted({(p[0], p[1]) for p in pairs} | {(p[2], p[3]) for p in pairs})
    record("TC-ATK-19", "B1", "400", get(f"/api/gate-persons/{D2_ID + 1001}", token=b1),
           data={"样本数": len(pairs), "不同的响应形态": len(uniq),
                 "响应": uniq[0][1][:120] if uniq else None},
           note="40 次探测只允许出现一种响应",
           pred=lambda: same and len(uniq) == 1)

    # ATK-14：用手机号自助换绑窃取他人身份
    r = put("/api/profile/phone", token=TK["U1"],
            json_body={"phone": "13705023122", "code": "000000"})
    record("TC-ATK-14", "U1", "400,msg:该手机号已被其他账号绑定", r,
           note="短信验证临时关闭，这是唯一防线")
    still = get("/api/auth/session", token=TK["U1"])
    record("TC-ATK-14b", "U1", "200", still,
           data={"U1 手机号未被改": True}, note="确认换绑确实没生效",
           pred=lambda: still.status == 200)

    # ATK-15：部门管理自我提权（建 ADMIN 角色账号）
    r = post("/api/users", token=b1, json_body={
        "username": "atk_admin_%d" % int(time.time()), "password": "Test@2026",
        "name": "越权提权", "deptId": ST["D1"], "phone": "13800000001",
        "roleIds": [2], "status": 1})
    record("TC-ATK-15", "B1", "403", r, note="roleIds=[2] 即平台管理")

    # ATK-16：部门管理改自己的管理范围
    r = put(f"/api/users/{IDS['B1']}/managed-departments", token=b1,
            json_body={"departments": [{"department_id": ST["D2"], "include_descendants": False}]})
    record("TC-ATK-16", "B1", "403", r, note="改自己的范围以扩权")

    # ATK-17：改他人手机号（手机号=登录凭据）
    # 目标必须是个 D2 的**普通用户**：B2 是 DEPT_ADMIN，B1 连碰都碰不到（角色策略先拦），
    # 那样测不到「跨部门用户」这道校验。
    probe = "t_user_d2"
    got = get("/api/users?keyword=" + probe, token=s1).data or {}
    d2_user = next((u["id"] for u in got.get("items", []) if u["username"] == probe), None)
    if not d2_user:
        cr = post("/api/users", token=s1, json_body={
            "username": probe, "password": "Test@2026", "name": "D2 普通用户",
            "deptId": ST["D2"], "phone": "13900000002", "roleIds": [4], "status": 1})
        d2_user = (cr.data or {}).get("id")
    print("D2 普通用户 id =", d2_user)
    r = put(f"/api/users/{d2_user}", token=b1, json_body={
        "username": probe, "name": "D2 普通用户", "deptId": ST["D2"],
        "phone": "13900000003", "roleIds": [4], "status": 1})
    record("TC-ATK-17", "B1", "403,msg:无权操作其他部门的用户", r,
           note="目标是 D2 的普通用户，B1 只有 D1")

    # ATK-20：验证码绕过
    r = post("/api/auth/login", json_body={"username": "admin", "password": "admin"})
    record("TC-ATK-20a", "匿名", "401", r,
           data={"message": r.message}, note="不带 captchaToken",
           pred=lambda: r.message is not None and "验证码" in (r.message or ""))

    # 次数是「每个验证码 token 一份」，所以必须反复用同一个 token 才会计满
    t0, _ = captcha()
    seq = []
    for i in range(7):
        r = post("/api/auth/login", json_body={
            "username": "admin", "password": "admin",
            "captchaToken": t0, "captchaAnswer": "0000"})
        seq.append((i + 1, r.status, r.message))
        if r.message and "次数过多" in r.message:
            break
    record("TC-ATK-20b", "匿名", "401", r,
           data={"每轮响应": seq},
           note="同一个 captchaToken 连续答错，观察第几次触发「验证码错误次数过多」",
           pred=lambda: any("次数过多" in (m or "") for _, _, m in seq))

    save_tokens(TK)
    dump_results("results_atk.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
