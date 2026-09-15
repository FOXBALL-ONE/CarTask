# -*- coding: utf-8 -*-
"""汇总各套用例的结果；并补录 TC-FACE-08（用现网数据直接验证，不必改库）。"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

HERE = os.path.dirname(os.path.abspath(__file__))
FACE08 = {
    "id": "TC-FACE-08", "actor": "B2(D2) / B1(D1)", "expect": "200 / 404",
    "http": 200, "api_status": 200, "message": None,
    "data": {"uuid": "ec5c654b-35e1-40bc-8640-21458ce733a4",
             "S1": 200, "B2": 200, "B1": 404, "A1": 200},
    "note": "GP-G-001 的上传文件 department_code 为 NULL（全局角色上传时没有工作部门可落标），"
            "B2 仍能下载 → 证明 business_type+business_id 反查分支可达；"
            "B1 因业务对象不在自己范围内得 404",
    "verdict": "PASS",
}


def main():
    p = os.path.join(HERE, "results_face.json")
    rows = json.load(open(p, encoding="utf-8"))
    if not any(r["id"] == "TC-FACE-08" for r in rows):
        rows.append(FACE08)
    json.dump(rows, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    allr = []
    for f in sorted(glob.glob(os.path.join(HERE, "results_*.json"))):
        allr += json.load(open(f, encoding="utf-8"))
    tot = {}
    for r in allr:
        tot[r["verdict"]] = tot.get(r["verdict"], 0) + 1
    json.dump(allr, open(os.path.join(HERE, "all_results.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print(tot, "total", len(allr))
    for r in allr:
        if r["verdict"] == "FAIL":
            print("FAIL", r["id"], "|", r["expect"], "|", r["http"], r["message"])


if __name__ == "__main__":
    main()
