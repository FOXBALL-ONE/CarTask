# -*- coding: utf-8 -*-
"""修复后的实机回归。

与 `t_*.py` 那套「干净基线」用例的区别：库里已经累积了上一轮 228 条门禁人员、
9 条删除申请、若干测试账号（批量清理被安全策略拦住，见报告第 7 节），
所以这里**不用绝对条数**断言，改成「与基线对比」和「两种情形必须一致」。

覆盖范围：
  A. F1 工作部门切换（原来报成功、实际没写进会话）
  B. F2 multipart 上限与提示
  C. F3/F4 门禁授权的数据范围 + 创建不再 500
  D. F5 错误消息中文化
  E. F6 验证码不再进日志
  F. G2 普通用户可以读自己的图片
  G. G4 跨部门撞号提示一致
  H. 回归：范围隔离、状态机、删除申请、文件可见性、SELF 记录
"""
import os
import re
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, delete, login, load_state, load_tokens,
                     save_tokens, record, dump_results, redis_client, session)
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
EPOCH = int(time.time())
LOG = ("C:/Users/28637/AppData/Local/JetBrains/IntelliJIdea2026.2/tmp/"
       "ij_run__CarTaskApplication_12615141862790080773.log")


def sess(tok):
    d = session(tok).data or {}
    return (d.get("scope"), d.get("working_department_id"))


def total(tok, path="/api/gate-persons?pageSize=1"):
    return (get(path, token=tok).data or {}).get("total")


def main():
    s1, a1, b2 = TK["S1"], TK["A1"], TK["B2"]
    u1, u3 = TK["U1"], TK["U3"]
    # 上一轮的 R-A9 会撤销 B1 的全部会话（替换管理范围的设计行为），tokens.json 里那份已经失效。
    b1, _ = login("t_dept_d1", "Test@2026x")
    TK["B1"] = b1
    rd = redis_client()

    print("\n===== A. F1 工作部门切换 =====")
    print("切换前:", sess(a1))
    r = put(f"/api/auth/working-department?department_id={D1}", token=a1)
    after = sess(a1)
    record("R-A1", "A1(平台管理)", "200", r,
           data={"PUT": r.message, "切换后会话": after},
           note="F1 修复：切换后再发一次请求，会话必须真的收窄",
           pred=lambda: after == ("DEPARTMENT", D1))

    # 顺带确认 Redis 里的会话正文确实被改了（不只是响应看着对）
    stored = rd.get_session(a1).get("working_department_id")
    record("R-A2", "A1", "200", r, data={"Redis 会话里的 working_department_id": stored},
           note="直连 Redis 核对写入真的落盘", pred=lambda: stored == D1)

    # 收窄后范围确实变小
    narrowed = total(a1)
    rd.set_working_department(a1, None)
    restored = total(a1)
    record("R-A3", "A1", "200", r, data={"收窄到 D1": narrowed, "回到全部": restored},
           note="收窄后的可见条数必须严格少于全局",
           pred=lambda: narrowed < restored)

    # 部门管理多部门切换
    tok_d1d2, _ = login("t_dept_d1d2", "Test@2026x")
    before_d1d2 = sess(tok_d1d2)
    put(f"/api/auth/working-department?department_id={D2}", token=tok_d1d2)
    after_d1d2 = sess(tok_d1d2)
    record("R-A4", "DEPT_ADMIN(管 D1+D2)", "200", r,
           data={"前": before_d1d2, "后": after_d1d2},
           note="部门管理切到 D2 同样必须生效",
           pred=lambda: after_d1d2 == ("DEPARTMENT", D2))
    put(f"/api/auth/working-department?department_id={D1}", token=tok_d1d2)

    # 原有的拒绝语义不能回退
    r = put(f"/api/auth/working-department?department_id={D2}", token=b1)
    record("R-A5", "B1", "403,msg:无权切换到该部门", r)
    r = put("/api/auth/working-department", token=b1)
    record("R-A6", "B1", "403,msg:必须选择一个工作部门", r)
    r = put("/api/auth/working-department", token=u1)
    record("R-A7", "U1", "403,msg:普通用户没有工作部门", r)
    r = put("/api/auth/working-department?department_id=99999", token=s1)
    record("R-A8", "S1", "400,msg:部门不存在", r)

    # 本次回归准备阶段新发现：把同一份管理范围再保存一次会撞唯一约束报 500。
    payload = {"departments": [{"department_id": D1, "include_descendants": False}]}
    b1_id = ST["ids"]["B1"]
    first = put(f"/api/users/{b1_id}/managed-departments", token=s1, json_body=payload)
    again = put(f"/api/users/{b1_id}/managed-departments", token=s1, json_body=payload)
    record("R-A9", "S1", "200", again,
           data={"第一次": first.status, "重复提交": again.status, "message": again.message},
           note="重复提交同一份部门管理范围不应 500（Hibernate 先 INSERT 后 DELETE 的顺序坑）",
           pred=lambda: first.status == 200 and again.status == 200)

    # 替换部门管理范围会 incrementTokenVersion，主动撤销该用户的全部会话（设计如此），
    # 所以 B1 的令牌在上一步就失效了，后面所有用到 B1 的用例都必须重新登录。
    b1, _ = login("t_dept_d1", "Test@2026x")
    TK["B1"] = b1
    print("B1 重新登录:", "OK" if b1 else "失败")

    print("\n===== B. F2 multipart 上限 =====")
    def probe(idx, mb):
        # 每个探针用各自的手机号/身份证号，否则会撞上「身份证号已存在」而测不到体积校验。
        return post("/api/gate-persons", token=b1,
                    form={"code": f"MULTI-{idx}-{EPOCH}", "dept": D1N, "name": "体积探测",
                          "phone": "144%08d" % ((EPOCH + idx) % 100000000),
                          "idCard": "350102199009%02d%04d" % (idx % 100, (EPOCH + idx) % 10000)},
                    files=[("face", "big.png",
                            b"\x89PNG\r\n\x1a\n" + os.urandom(int(mb * 1024 * 1024)),
                            "image/png")])

    small = probe(1, 0.9)
    mid = probe(2, 1.1)
    over = probe(3, 2.1)
    huge = probe(4, 6)
    record("R-B1", "B1", "201", small, note="0.9MB 应通过")
    record("R-B2", "B1", "201", mid,
           data={"1.1MB": mid.status, "message": mid.message},
           note="F2 修复：1.1MB 以前会被容器拦成 413，现在应进业务校验并通过",
           pred=lambda: mid.status == 201)
    record("R-B3", "B1", "400,msg:人脸照片不能超过 2MB", over,
           data={"2.1MB": over.status, "message": over.message},
           note="F2 修复：超过业务上限 2MB 时给出中文业务提示（容器上限已抬到 5MB，"
                "业务校验这才真正执行得到）",
           pred=lambda: over.status == 400 and "2MB" in (over.message or ""))
    record("R-B4", "B1", "413,msg:上传文件超过大小限制", huge,
           data={"6MB": huge.status, "message": huge.message},
           note="F2：超过容器兜底上限时仍然是 413，但提示已中文化",
           pred=lambda: huge.status == 413 and "上传文件超过大小限制" in (huge.message or ""))

    print("\n===== C. F3/F4 门禁授权 =====")
    def mk(tok, dept_id, tag):
        return post("/api/access-controls", token=tok, json_body={
            "name": f"回归授权{tag}", "phone": "14700000001",
            "personNumber": f"RAC-{tag}-{EPOCH}", "accessControlList": "回归门禁点",
            "department": {"id": dept_id}})

    def ac_rows(tok):
        # 列表返回的是 Spring Page，字段是 content 而不是 items；total 字段在某些版本里不稳定，
        # 直接数 content 更可靠（page_size=100 足够装下当前数据量）。
        d = get("/api/access-controls?page=1&page_size=100", token=tok).data or {}
        return d.get("content") or []

    before_rows = len(ac_rows(s1))
    r = mk(b1, D1, "D1")
    after_rows = len(ac_rows(s1))
    ac_d1 = (r.data or {}).get("id")
    record("R-C1", "B1", "201", r,
           data={"id": ac_d1, "departmentId": (r.data or {}).get("departmentId"),
                 "departmentName": (r.data or {}).get("departmentName"),
                 "创建前条数": before_rows, "创建后条数": after_rows},
           note="F4 修复：不再 500；响应直接带 id，且条数只 +1（不再有「看不见的落库」）",
           pred=lambda: ac_d1 is not None
           and (r.data or {}).get("departmentName") == D1N
           and after_rows == before_rows + 1)

    r_d2 = mk(s1, D2, "D2")
    ac_d2 = (r_d2.data or {}).get("id")
    print("ac_d1=%s ac_d2=%s" % (ac_d1, ac_d2))

    r = mk(b1, D2, "BAD")
    record("R-C2", "B1", "403,msg:无权操作其他部门的数据", r,
           note="F3 修复：不能把授权建到范围外的部门")

    r = get(f"/api/access-controls/{ac_d2}", token=b1)
    record("R-C3", "B1", "400,msg:记录不存在", r,
           note="F3 修复：按 ID 读别部门的授权，与「不存在」同错")

    r_list = get("/api/access-controls?page=1&page_size=100", token=b1)
    items = (r_list.data or {}).get("content") or []
    dept_ids = sorted({i.get("departmentId") for i in items})
    record("R-C4", "B1", "200", r_list,
           data={"列表里出现的部门": dept_ids, "条数": len(items)},
           note="F3 修复：列表只应出现 D1",
           pred=lambda: dept_ids == [D1])

    r = post(f"/api/access-controls/{ac_d2}/review?approved=true&review_reason="
             + urllib.parse.quote("越权审核"), token=b1)
    record("R-C5", "B1", "400,msg:记录不存在", r, note="F3 修复：审核别部门的授权被拒")

    r = post(f"/api/access-controls/{ac_d1}/review?approved=true&review_reason="
             + urllib.parse.quote("本部门资料齐全"), token=b1)
    record("R-C6", "B1", "200", r, note="本部门的授权仍可正常审核",
           pred=lambda: (r.data or {}).get("reviewStatus") == "APPROVED")

    r = post(f"/api/access-controls/{ac_d1}/sync", token=b1)
    record("R-C7", "B1", "501,msg:门禁设备同步尚未接入", r,
           data={"http": r.status, "message": r.message},
           note="F4 修复：原来是 500 + 空消息，现在 501 + 明确说明")

    r = delete(f"/api/access-controls/{ac_d1}", token=b1)
    record("R-C8", "B1", "403", r, note="denyAll() 不变")

    print("\n===== D. F5 错误消息中文化 =====")
    r = get("/api/gate-persons", token=u1)
    record("R-D1", "U1", "403", r, data={"message": r.message},
           note="F5 修复：方法级安全拒绝不再回英文 Access Denied",
           pred=lambda: r.message == "没有操作权限")
    r = post("/api/gate-persons", token=b1,
             form={"code": f"NOFACE-{EPOCH}", "dept": D1N, "name": "缺人脸",
                   "phone": "145%08d" % (EPOCH % 100000000),
                   "idCard": "35010219901001%04d" % (EPOCH % 10000)})
    record("R-D2", "B1", "400,msg:缺少必需的请求部分", r,
           data={"message": r.message},
           note="F5 修复：缺少 multipart 部分给中文提示")
    r = get("/api/access-controls/99999999", token=b1)
    record("R-D3", "B1", "400,msg:记录不存在", r, note="业务错误消息仍保持中文")

    print("\n===== E. F6 验证码不进日志 =====")
    hits = 0
    if os.path.exists(LOG):
        with open(LOG, encoding="utf-8", errors="replace") as f:
            hits = f.read().count("验证码为")
    record("R-E1", "—", "200", get("/api/auth/captcha"),
           data={"日志路径": os.path.basename(LOG), "出现「验证码为」次数": hits},
           note="F6 修复：重启后的日志里不能再出现验证码明文",
           pred=lambda: hits == 0)

    print("\n===== F. G2 普通用户读自己的图片 =====")
    perms = session(u1).data.get("permissions", [])
    record("R-F1", "U1", "200", get("/api/auth/session", token=u1),
           data={"有 file:read": "file:read" in perms},
           note="G2 修复：USER 角色已授予 file:read", pred=lambda: "file:read" in perms)

    mine = get("/api/gate-persons?keyword=GP-D1-001&pageSize=10", token=s1)
    my_face = None
    for i in (mine.data or {}).get("items", []):
        if i["code"] == "GP-D1-001":
            my_face = i["face"]
    uid = re.search(r"/files/([0-9a-f-]+)/download", my_face or "").group(1)
    r = get(f"/api/files/{uid}/download", token=u1)
    record("R-F2", "U1", "200", r,
           data={"自己的门禁照片": r.status, "字节": len(r.body_bytes)},
           note="G2 修复：SELF 范围能取到本人门禁身份的照片",
           pred=lambda: r.status == 200)

    other = get("/api/gate-persons?keyword=GP-D2-001&pageSize=10", token=s1)
    o_face = next((i["face"] for i in (other.data or {}).get("items", [])
                   if i["code"] == "GP-D2-001"), None)
    o_uid = re.search(r"/files/([0-9a-f-]+)/download", o_face or "").group(1)
    r = get(f"/api/files/{o_uid}/download", token=u1)
    record("R-F3", "U1", "404", r,
           note="G2 修复不能顺带放开别人：SELF 范围外仍然是 404")

    print("\n===== G. G4 跨部门撞号提示一致 =====")
    def dup(code, idc, phone):
        return post("/api/gate-persons", token=b1,
                    form={"code": code, "dept": D1N, "name": "撞号探测",
                          "phone": phone, "idCard": idc},
                    files=[("face", "p.png", mkface(), "image/png")])

    in_code = dup("GP-D1-002", "35010219900201%04d" % (EPOCH % 10000),
                  "146%08d" % (EPOCH % 100000000))
    out_code = dup("GP-D2-001", "35010219900202%04d" % (EPOCH % 10000),
                   "147%08d" % (EPOCH % 100000000))
    record("R-G1", "B1", "400,msg:人员编号已存在", in_code, note="本部门编号撞号")
    record("R-G2", "B1", "400", out_code,
           data={"本部门": in_code.raw, "别部门": out_code.raw},
           note="G4 修复：两种情形的响应体必须逐字一致，不暴露归属",
           pred=lambda: in_code.raw == out_code.raw)

    in_idc = dup("RAC-IDC-IN-%d" % EPOCH, "350102199001010012",
                 "148%08d" % (EPOCH % 100000000))
    out_idc = dup("RAC-IDC-OUT-%d" % EPOCH, "350102199001010014",
                  "149%08d" % (EPOCH % 100000000))
    record("R-G3", "B1", "400", out_idc,
           data={"本部门": in_idc.raw, "别部门": out_idc.raw},
           note="G4：身份证号撞号同理（D1 与 D2 各一条已存在的身份证号）",
           pred=lambda: in_idc.raw == out_idc.raw)

    print("\n===== H. 回归：未改动的核心行为 =====")
    b1_codes = {i["code"] for i in (get("/api/gate-persons?pageSize=100", token=b1).data or {}).get("items", [])}
    b2_codes = {i["code"] for i in (get("/api/gate-persons?pageSize=100", token=b2).data or {}).get("items", [])}
    record("R-H1", "B1/B2", "200", get("/api/gate-persons?pageSize=100", token=b1),
           data={"B1 条数": len(b1_codes), "B2 条数": len(b2_codes),
                 "交集": sorted(b1_codes & b2_codes)},
           note="范围隔离：D1 与 D2 互不可见",
           pred=lambda: not (b1_codes & b2_codes))

    r = get(f"/api/gate-persons/{[i for i in (get('/api/gate-persons?pageSize=100', token=b2).data or {}).get('items', []) if i['code'] == 'GP-D2-001'][0]['id']}", token=b1)
    record("R-H2", "B1", "400,msg:人员不存在", r, note="跨部门按 ID 读仍被拒")

    # 状态机
    p = [i for i in (get("/api/gate-persons?pageSize=100", token=b1).data or {}).get("items", [])
         if i["code"] == "GP-D1-003"][0]
    r = put(f"/api/gate-persons/{p['id']}", token=b1, form={"name": p["name"] + "x"})
    after = get(f"/api/gate-persons/{p['id']}", token=s1).data
    record("R-H3", "B1", "200", r, data={"approveStatus": after["approveStatus"]},
           note="改内容仍会打回待审核",
           pred=lambda: after["approveStatus"] == "审核中")
    r = put(f"/api/gate-persons/{p['id']}/approve", token=b1)
    r2 = put(f"/api/gate-persons/{p['id']}/approve", token=b1)
    record("R-H4", "B1", "400,msg:该人员当前状态不允许审核", r2, note="状态机仍单向")

    # 删除申请链路
    r = delete(f"/api/gate-persons/{p['id']}", token=s1)
    record("R-H5", "S1", "403", r, note="物理删除仍对超管关闭")
    r = post(f"/api/gate-persons/{p['id']}/delete-requests", token=b1,
             json_body={"reason": "回归用例"})
    rec_id = (r.data or {}).get("id")
    record("R-H6", "B1", "201", r, note="提交删除申请")
    r = put(f"/api/gate-persons/delete-requests/{rec_id}/reject", token=b1)
    alive = get(f"/api/gate-persons/{p['id']}", token=s1)
    record("R-H7", "B1", "200", r, data={"人员仍在": alive.status},
           note="驳回不删人", pred=lambda: alive.status == 200)

    # SELF 记录
    r = get("/api/person-records?pageSize=100", token=u1)
    t1 = (r.data or {}).get("total")
    r3 = get("/api/person-records?pageSize=100", token=u3)
    t3 = (r3.data or {}).get("total")
    record("R-H8", "U1/U3", "200", r, data={"U1": t1, "U3": t3},
           note="SELF 范围：有归属的人看得到自己的、无归属的为 0",
           pred=lambda: t1 == 2 and t3 == 0)

    save_tokens(TK)
    dump_results("results_regress.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
