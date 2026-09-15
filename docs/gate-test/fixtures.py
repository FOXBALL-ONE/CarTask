# -*- coding: utf-8 -*-
"""阶段 1：前置测试数据（门禁人员 + 人员进出记录）。

走真实接口造数据；只有两处必须直连 SQL：
  - GP-ORPHAN：department_code 为 NULL 且 dept 是解析不出的自由文本，
    接口不可能造出来（scopeGuard 会拦），只能 INSERT。
  - person_access_record：该表没有任何写入接口（G3）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, post, request, load_state, load_tokens, save_state,
                     save_tokens, login)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()


def log(*a):
    print(*a, flush=True)


def create_person(token, code, dept, name, phone, id_card, face_bytes=None,
                  filename="face.png"):
    return post("/api/gate-persons", token=token,
                form={"code": code, "dept": dept, "name": name,
                      "phone": phone, "idCard": id_card},
                files=[("face", filename, face_bytes or mkface(), "image/png")])


def main():
    d1n, d2n = ST["D1_NAME"], ST["D2_NAME"]
    face = mkface()
    out = {}

    specs = [
        ("GP-D1-001", "B1", d1n, "章规希", "18950315520", "350102199001010011"),
        ("GP-D1-002", "B1", d1n, "门禁测试甲", "13000000201", "350102199001010012"),
        ("GP-D1-003", "B1", d1n, "门禁测试乙", "13000000202", "350102199001010013"),
        ("GP-D2-001", "B2", d2n, "门禁测试丙", "13000000203", "350102199001010014"),
    ]
    for code, who, dept, name, phone, idc in specs:
        r = create_person(TK[who], code, dept, name, phone, idc, face)
        log(f"{code} via {who}: http={r.status} msg={r.message} id={r.data and r.data.get('id')}")
        if r.data:
            out[code] = r.data

    save_state("persons.json", out)
    log("persons:", {k: v["id"] for k, v in out.items()})
    log("face of GP-D1-001:", out.get("GP-D1-001", {}).get("face"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
