# -*- coding: utf-8 -*-
"""5.8 人脸文件 TC-FACE-*（10~12 依赖删除/改编号，放到 t_face2.py）"""
import os
import re
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, post, login, load_state, load_tokens, record,
                     dump_results, save_tokens, save_state, redis_client)

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]

FACE1 = P["GP-D1-001"]["face"]          # http://127.0.0.1:8080/api/files/<uuid>/download
FACE2 = P["GP-D1-002"]["face"]
UUID1 = re.search(r"/files/([0-9a-f-]+)/download", FACE1).group(1)
PATH1 = "/api/files/%s/download" % UUID1


def main():
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    rd = redis_client()

    rd.set_working_department(a2, D1)
    for tc, who, key, exp, note in [
        ("TC-FACE-01", "B1(D1)", "B1", "200", "同部门可见"),
        ("TC-FACE-03", "A1(未切部门)", "A1", "200", "ALL"),
        ("TC-FACE-04", "A2(切 D1)", "A2", "200", "D1 在内"),
        ("TC-FACE-06", "S1", "S1", "200", "超管全局"),
        ("TC-FACE-02", "B2(D2)", "B2", "404,msg:文件不存在", "跨部门；与真不存在同错"),
        ("TC-FACE-07", "U1", "U1", "403", "USER 无 file:read（先于范围判定）"),
    ]:
        r = get(PATH1, token=TK[key])
        record(tc, who, exp, r, data={"uuid": UUID1, "bytes": len(r.body_bytes)},
               note=note)

    # A2 收窄到 D2 后应看不到 D1 的人脸
    rd.set_working_department(a2, D2)
    r = get(PATH1, token=a2)
    record("TC-FACE-05", "A2(切 D2)", "404", r, note="已收窄到 D2")
    rd.set_working_department(a2, D1)

    # TC-FACE-09 不存在的 uuid：响应须与 TC-FACE-02 一致
    ghost = str(uuid.uuid4())
    r9 = get("/api/files/%s/download" % ghost, token=TK["B1"])
    record("TC-FACE-09", "B1", "404,msg:文件不存在", r9, note="uuid=%s" % ghost)
    r2 = get(PATH1, token=TK["B2"])
    record("TC-FACE-02=09", "B1/B2", "404", r9,
           data={"cross_dept": [r2.status, r2.message, len(r2.body_bytes)],
                 "not_exist": [r9.status, r9.message, len(r9.body_bytes)]},
           note="跨部门与不存在响应须一致",
           pred=lambda: (r2.status, r2.raw) == (r9.status, r9.raw))

    # TC-FACE-08 业务对象反查分支：把 department_code 抹掉，只剩 business_type/business_id
    r = get(PATH1, token=TK["B1"])
    print("准备抹掉 department_code 前 B1:", r.status)
    save_state("face_before.json", {"uuid1": UUID1, "face1": FACE1, "face2": FACE2})
    print("待执行 SQL：UPDATE stored_files SET department_code=NULL WHERE id='%s'" % UUID1)

    # GP-D1-002 的人脸 uuid 也记下来，供 5.8 的补充用例使用
    save_state("face_ids.json", {
        "GP-D1-001": UUID1,
        "GP-D1-002": re.search(r"/files/([0-9a-f-]+)/download", FACE2).group(1),
    })

    save_tokens(TK)
    dump_results("results_face.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
