# -*- coding: utf-8 -*-
"""5.7 导入导出 TC-XLS-*（导出部分先行，避免后续录入测试改变基数）"""
import os
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, post, login, load_state, load_tokens, record,
                     dump_results, save_tokens, redis_client)
from xls import build_gate_person_xlsx, data_rows, read_sheet

ST = load_state("state.json")
TK = load_tokens()
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
D1C, D2C = ST["D1_CODE"], ST["D2_CODE"]


def main():
    a2, _ = login("t_admin", "Test@2026x")
    redis_client().set_working_department(a2, D1)
    TK["A2"] = a2

    # TC-XLS-01 模板
    r = get("/api/excel/gate-persons/template", token=TK["B1"])
    sheet, rows = (None, None)
    if r.status == 200:
        try:
            sheet, rows = read_sheet(r.body_bytes)
        except Exception as e:  # noqa: BLE001
            rows = f"解析失败 {e}"
    hdr = rows[0] if isinstance(rows, list) and rows else None
    record("TC-XLS-01", "B1", "200", r, data={"sheet": sheet, "header": hdr},
           note="表头须含 人员编号/部门/姓名/手机号/身份证号",
           pred=lambda: hdr is not None and all(
               h in [str(x) for x in hdr] for h in
               ["人员编号", "部门", "姓名", "手机号", "身份证号"]))

    # TC-XLS-02 普通用户拿不到模板
    for who in ("U1", "U3"):
        r = get("/api/excel/gate-persons/template", token=TK[who])
        record("TC-XLS-02", who, "403", r, note="USER 无 gate-person:read")

    # TC-XLS-03/04/05/06/07 导出范围裁剪
    exp = [("TC-XLS-03", "B1(D1)", "B1", 3, "按范围裁剪：只含 D1"),
           ("TC-XLS-04", "B2(D2)", "B2", 1, "只含 D2；与 03 行数不同即证明裁剪生效"),
           ("TC-XLS-05", "A1(未切)", "A1", 5, "全局"),
           ("TC-XLS-06", "A2(切 D1)", "A2", 3, "同 B1"),
           ("TC-XLS-07", "S1", "S1", 5, "全局")]
    exported = {}
    for tc, who, key, want, note in exp:
        r = get("/api/excel/gate-persons/export", token=TK[key])
        n = None
        codes = []
        if r.status == 200:
            try:
                rows = data_rows(r.body_bytes)
                n = len(rows)
                codes = sorted(str(x[1]) for x in rows)  # 第 2 列是「人员编号」
            except Exception as e:  # noqa: BLE001
                n = f"解析失败 {e}"
        exported[key] = {"n": n, "codes": codes}
        record(tc, who, "200", r, data={"rows": n, "codes": codes}, note=note,
               pred=lambda n=n, want=want: n == want)

    # TC-XLS-17 USER 导入/导出
    r = post("/api/excel/gate-persons/import", token=TK["U1"],
             files=[("file", "a.xlsx", build_gate_person_xlsx([]), "application/vnd.ms-excel")])
    record("TC-XLS-17a", "U1", "403", r, note="USER 无 gate-person:manage")
    r = get("/api/excel/gate-persons/export", token=TK["U1"])
    record("TC-XLS-17b", "U1", "403", r, note="USER 无 gate-person:export")

    # TC-XLS-18 DEPT_ADMIN 拿不到「全部模板」
    r = get("/api/excel/all/template", token=TK["B1"])
    record("TC-XLS-18", "B1", "403", r, note="需要一串 manage，DEPT_ADMIN 被排除")

    save_tokens(TK)
    dump_results("results_xls_export.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
