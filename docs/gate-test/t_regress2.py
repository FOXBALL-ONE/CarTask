# -*- coding: utf-8 -*-
"""针对本次改动的补充实机探测：只打「改过但定向回归没覆盖」的接口。

上一轮 t_regress.py 覆盖的是单条路径，这次补的是：
  - access_control 的 batch 三件套（createBatch / getBatch / updateBatch）是否同样按范围裁剪
  - PUT /{id} 的跨部门越权
  - 文件**元数据**接口（GET /api/files/{id}）对 USER 放开后是否也走范围
  - 新建授权时「部门不存在」「不给部门」两种入参
"""
import os
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, login, load_state, load_tokens, save_tokens,
                     record, dump_results, redis_client, session)

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
EPOCH = int(time.time())


def ac(tok, page=1, size=100):
    d = get(f"/api/access-controls?page={page}&page_size={size}", token=tok).data or {}
    return d.get("content") or []


def main():
    s1, b1 = TK["S1"], TK["B1"]
    b1, _ = login("t_dept_d1", "Test@2026x")   # 上轮 R-A9 撤销过 B1 的会话
    TK["B1"] = b1

    # 找两条分属 D1 / D2 的现有授权
    all_rows = ac(s1)
    d1_row = next((r for r in all_rows if r["departmentId"] == D1), None)
    d2_row = next((r for r in all_rows if r["departmentId"] == D2), None)
    print("D1 授权:", d1_row and d1_row["id"], " D2 授权:", d2_row and d2_row["id"])
    if not d1_row or not d2_row:
        print("缺少可用的 D1/D2 授权记录，先建两条")
        d1_row = post("/api/access-controls", token=s1, json_body={
            "name": "批测D1", "personNumber": "BAT-D1", "department": {"id": D1}}).data
        d2_row = post("/api/access-controls", token=s1, json_body={
            "name": "批测D2", "personNumber": "BAT-D2", "department": {"id": D2}}).data

    print("\n===== 1. access_control 批量接口的范围裁剪 =====")
    r = get(f"/api/access-controls/batch?id={d1_row['id']}&id={d2_row['id']}", token=b1)
    record("S-1a", "B1", "400", r,
           data={"status": r.status, "message": r.message},
           note="getBatch 混入别部门的 id：应与「不存在」共用同一句「部分记录不存在」")

    r = get(f"/api/access-controls/batch?id={d1_row['id']}", token=b1)
    record("S-1b", "B1", "200", r, data={"status": r.status},
           note="getBatch 只取本部门的 id：应正常返回")

    r = get(f"/api/access-controls/batch?id={d2_row['id']}", token=b1)
    record("S-1c", "B1", "400,msg:部分记录不存在", r,
           note="getBatch 单取别部门的 id：必须被挡住")

    # updateBatch 混入别部门的 id
    body = [{"id": d1_row["id"], "name": d1_row["name"]},
            {"id": d2_row["id"], "name": "越权改名"}]
    r = put("/api/access-controls/batch", token=b1, json_body=body)
    after = next((x for x in ac(s1) if x["id"] == d2_row["id"]), {})
    record("S-1d", "B1", "400", r,
           data={"status": r.status, "message": r.message, "D2 名称": after.get("name")},
           note="updateBatch 混入别部门的 id：必须整批失败且不落半步结果",
           pred=lambda: after.get("name") == d2_row["name"])

    # createBatch 往别部门建
    r = post("/api/access-controls/batch", token=b1, json_body=[
        {"name": "批建本部门", "personNumber": "BATC-OK-%d" % EPOCH, "department": {"id": D1}}])
    record("S-1e", "B1", "201", r, note="createBatch 建到本部门：应成功")

    r = post("/api/access-controls/batch", token=b1, json_body=[
        {"name": "批建别部门", "personNumber": "BATC-BAD-%d" % EPOCH, "department": {"id": D2}}])
    record("S-1f", "B1", "403,msg:无权操作其他部门的数据", r,
           note="createBatch 建到范围外：必须被挡住")

    # 单条 PUT 跨部门
    r = put(f"/api/access-controls/{d2_row['id']}", token=b1,
            json_body={"id": d2_row["id"], "name": "越权改名"})
    record("S-1g", "B1", "400,msg:记录不存在", r, note="单条 PUT 跨部门：与「不存在」同错")

    # 把自己的记录改到别的部门
    r = put(f"/api/access-controls/{d1_row['id']}", token=b1,
            json_body={"id": d1_row["id"], "name": d1_row["name"], "department": {"id": D2}})
    moved = next((x for x in ac(s1) if x["id"] == d1_row["id"]), {})
    record("S-1h", "B1", "403,msg:无权操作其他部门的数据", r,
           data={"把数据挪到 D2": r.status, "实际部门": moved.get("departmentId")},
           note="不能把自己的授权挪出范围",
           pred=lambda: moved.get("departmentId") == D1)

    print("\n===== 2. 新建授权的入参边界 =====")
    r = post("/api/access-controls", token=b1, json_body={
        "name": "不存在的部门", "personNumber": "S-NODEPT-1-%d" % EPOCH, "department": {"id": 999999}})
    record("S-2a", "B1", "400,msg:部门不存在", r)

    r = post("/api/access-controls", token=b1, json_body={
        "name": "不给部门", "personNumber": "S-NODEPT-2-%d" % EPOCH})
    record("S-2b", "B1", "400,msg:必须指定部门", r,
           note="受限范围下不给部门会造出连自己都看不见的记录")

    r = post("/api/access-controls", token=s1, json_body={
        "name": "超管不给部门", "personNumber": "S-NODEPT-3-%d" % EPOCH})
    record("S-2c", "S1", "201", r,
           note="全局角色不给部门是允许的（该记录对受限角色不可见，属 fail-closed 预期）")

    print("\n===== 3. 文件元数据接口对 USER 放开后的范围 =====")
    mine = get("/api/gate-persons?keyword=GP-D1-001&pageSize=10", token=s1).data["items"]
    my_face = next(i["face"] for i in mine if i["code"] == "GP-D1-001")
    uid_mine = my_face.split("/files/")[1].split("/")[0]
    other = get("/api/gate-persons?keyword=GP-D2-001&pageSize=10", token=s1).data["items"]
    o_face = next(i["face"] for i in other if i["code"] == "GP-D2-001")
    uid_other = o_face.split("/files/")[1].split("/")[0]

    r = get(f"/api/files/{uid_mine}", token=TK["U1"])
    record("S-3a", "U1", "200", r,
           data={"status": r.status, "original_filename": (r.data or {}).get("original_filename")},
           note="文件**元数据**接口也应放开给 USER，且只能看自己的")

    r = get(f"/api/files/{uid_other}", token=TK["U1"])
    record("S-3b", "U1", "404,msg:文件不存在", r,
           note="别人的文件元数据：与下载接口一样 404")

    r = post("/api/files", token=TK["U1"],
             files=[("file", "x.png", b"\x89PNG\r\n\x1a\n" + b"0" * 32, "image/png")])
    record("S-3c", "U1", "403", r, note="上传仍然只给管理角色，USER 不该拿到 file:upload")

    print("\n===== 补充：代码审查发现的修复 =====")
    # person_number 是全局唯一，但约束名是 Hibernate 生成的哈希串，GlobalExceptionHandler
    # 没法按名字映射 —— 修复前撞号会落到 500 + 空消息。
    pn = "RVW-DUP-%d" % EPOCH
    ok1 = post("/api/access-controls", token=s1, json_body={
        "name": "编号甲", "personNumber": pn, "department": {"id": D1}})
    dup = post("/api/access-controls", token=s1, json_body={
        "name": "编号乙", "personNumber": pn, "department": {"id": D2}})
    record("S-5a", "S1", "400,msg:人员编号已存在", dup,
           data={"第一次": ok1.status, "撞号": dup.status, "message": dup.message},
           note="撞号要给可读提示，不能是 500 空消息")

    # 授权类型：不存在的 id 也要在写库前拒绝（修复前是 PostgreSQL 外键异常 → 500）
    bad_type = post("/api/access-controls", token=s1, json_body={
        "name": "坏类型", "personNumber": "RVW-TYPE-%d" % EPOCH,
        "department": {"id": D1}, "accessControlPermission": {"id": 999999}})
    record("S-5b", "S1", "400,msg:门禁授权类型不存在", bad_type)

    # 取回来再原样提交，不能把 access_control_type_id 清掉
    types = get("/api/access-control-types?page=1&page_size=5", token=s1).data or {}
    type_id = next((t["id"] for t in (types.get("content") or types.get("items") or [])), None)
    if type_id is None:
        type_id = (post("/api/access-control-types", token=s1,
                        json_body={"accessControlName": "回归类型%d" % EPOCH}).data or {}).get("id")
    made = post("/api/access-controls", token=s1, json_body={
        "name": "带类型回归", "personNumber": "RVW-KEEP-%d" % EPOCH,
        "department": {"id": D1}, "accessControlPermission": {"id": type_id}})
    before_type = (made.data or {}).get("accessControlPermissionId")
    back = put(f"/api/access-controls/{(made.data or {}).get('id')}", token=s1, json_body={
        "id": (made.data or {}).get("id"), "name": (made.data or {}).get("name"),
        "personNumber": (made.data or {}).get("personNumber"),
        "department": {"id": D1}})
    after_type = (back.data or {}).get("accessControlPermissionId")
    record("S-5c", "S1", "200", back,
           data={"创建时类型": before_type, "原样提交后类型": after_type, "期望": type_id},
           note="GET→PUT 原样提交不能把 access_control_type_id 清空（修复前实测被清掉）",
           pred=lambda: before_type == after_type == type_id)

    print("\n===== 4. 收紧后仍应正常的能力 =====")
    r = get(f"/api/access-controls/{d1_row['id']}", token=b1)
    record("S-4a", "B1", "200", r, note="本部门授权仍可正常读取")
    r = get("/api/files/%s/download" % uid_mine, token=b1)
    record("S-4b", "B1", "200", r, note="B1 仍能取到本部门人员的脸图")

    save_tokens(TK)
    dump_results("results_regress2.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
