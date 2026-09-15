# -*- coding: utf-8 -*-
"""5.8 人脸文件 TC-FACE-10/11/12（依赖删除、改编号、改部门）"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, load_state, load_tokens, record,
                     dump_results, save_tokens, save_state, redis_client)
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
EPOCH = int(time.time())


def mk(tok, code, dept, name, phone, idc):
    r = post("/api/gate-persons", token=tok,
             form={"code": code, "dept": dept, "name": name,
                   "phone": phone, "idCard": idc},
             files=[("face", "p.png", mkface(), "image/png")])
    return r.data


def uuid_of(url):
    return re.search(r"/files/([0-9a-f-]+)/download", url or "").group(1)


def main():
    b1, s1 = TK["B1"], TK["S1"]

    # TC-FACE-11：改编号后旧 URL 仍可下载（文件本身没动，只是业务挂载点重锚）
    p = mk(b1, "FACE-RN-%d" % EPOCH, D1N, "改编号探针", "141%08d" % (EPOCH % 100000000),
           "35010219900801%04d" % (EPOCH % 10000))
    old_uuid = uuid_of(p["face"])
    r = put(f"/api/gate-persons/{p['id']}", token=b1,
            form={"code": "FACE-RN2-%d" % EPOCH})
    print("改编号:", r.status, r.message)
    r = get(f"/api/files/{old_uuid}/download", token=b1)
    record("TC-FACE-11", "B1", "200", r,
           data={"uuid": old_uuid, "改编号后旧 URL": r.status},
           note="relinkBusiness：业务 id 重挂到新 code，文件本身未动")

    # TC-FACE-12：把人员从 D1 挪到 D2 后，B1 取旧 URL 应 404
    p2 = mk(b1, "FACE-MV-%d" % EPOCH, D1N, "挪部门探针", "142%08d" % (EPOCH % 100000000),
            "35010219900802%04d" % (EPOCH % 10000))
    u2 = uuid_of(p2["face"])
    before = get(f"/api/files/{u2}/download", token=b1)
    r = put(f"/api/gate-persons/{p2['id']}", token=s1, form={"dept": D2N})
    print("挪到 D2:", r.status, r.message)
    after = get(f"/api/files/{u2}/download", token=b1)
    record("TC-FACE-12", "B1", "404", after,
           data={"挪之前": before.status, "挪之后": after.status},
           note="归属部门快照随重挂载变更",
           pred=lambda: before.status == 200 and after.status == 404)

    # TC-FACE-10：人员被删除后其脸不可再下载
    p3 = mk(b1, "FACE-DEL-%d" % EPOCH, D1N, "删除探针", "143%08d" % (EPOCH % 100000000),
            "35010219900803%04d" % (EPOCH % 10000))
    u3 = uuid_of(p3["face"])
    b4 = get(f"/api/files/{u3}/download", token=b1)
    dr = post(f"/api/gate-persons/{p3['id']}/delete-requests", token=b1,
              json_body={"reason": "文件生命周期用例"})
    ap = put(f"/api/gate-persons/delete-requests/{(dr.data or {}).get('id')}/approve", token=b1)
    print("删人:", ap.status, ap.message)
    aft = get(f"/api/files/{u3}/download", token=b1)
    record("TC-FACE-10", "B1", "404", aft,
           data={"删除前": b4.status, "删除后": aft.status},
           note="unlinkBusiness：人员已删除就不能再按编号被反查下载",
           pred=lambda: b4.status == 200 and aft.status == 404)

    save_tokens(TK)
    dump_results("results_face2.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
