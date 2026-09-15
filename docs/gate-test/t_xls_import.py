# -*- coding: utf-8 -*-
"""5.7 导入导出 TC-XLS-*（导入部分）"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, post, login, load_state, load_tokens, record,
                     dump_results, save_tokens, redis_client)
from xls import build_gate_person_xlsx

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
EPOCH = int(time.time())
IDX = [0]


def imp(tok, rows, name="imp.xlsx"):
    return post("/api/excel/gate-persons/import", token=tok,
                files=[("file", name, build_gate_person_xlsx(rows),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")])


def row(tag, dept, phone=None, idc=None):
    IDX[0] += 1
    n = IDX[0]
    return ("XLS-%s-%d-%d" % (tag, EPOCH, n),
            dept,
            "导入测试%s%02d" % (tag, n),
            phone or "136%08d" % ((EPOCH + n) % 100000000),
            idc or "350102199005%02d%04d" % (n % 100, (EPOCH + n) % 10000))


def main():
    b1, b2, s1, a1 = TK["B1"], TK["B2"], TK["S1"], TK["A1"]
    rd = redis_client()

    # TC-XLS-08：表里填了别的部门 → 带行号报错，不静默改写
    r = imp(b1, [row("o1", D1N), row("o2", D2N)])
    record("TC-XLS-08", "B1", "400", r,
           data={"message": r.message},
           note="必须带行号且指明当前工作部门",
           pred=lambda: r.message and "第3行" in r.message
           and "同步车主" in r.message and "AUTO-05800DE49C" in r.message)

    # TC-XLS-09：部门留空 → 落到当前工作部门
    r = imp(b1, [row("e1", None), row("e2", None)])
    record("TC-XLS-09", "B1", "201", r, data=r.data,
           note="部门留空时用 forcedDepartment 兜底",
           pred=lambda: (r.data or {}).get("count") == 2)

    # TC-XLS-10：部门=当前工作部门
    r = imp(b1, [row("m1", D1N)])
    record("TC-XLS-10", "B1", "201", r, data=r.data)

    # TC-XLS-11：全局范围不强制部门，落到表里填的 D2
    r = imp(a1, [row("g1", D2N)])
    record("TC-XLS-11", "A1(未切)", "201", r, data=r.data,
           note="全局范围不强制部门，应落到 D2")

    # TC-XLS-12：文件内编号重复
    dup = row("d1", D1N)
    r = imp(b1, [dup, (dup[0], D1N, "重复2", "137%08d" % (EPOCH % 100000000),
                       "35010219900601%04d" % (EPOCH % 10000))])
    record("TC-XLS-12", "B1", "400,msg:导入文件中的人员编号不能重复", r)

    # TC-XLS-13：编号与库中已存在（含其它部门的 GP-D2-001）相同
    r = imp(b1, [("GP-D2-001", None, "跨部门撞号", "138%08d" % (EPOCH % 100000000),
                  "35010219900701%04d" % (EPOCH % 10000))])
    record("TC-XLS-13", "B1", "400,msg:导入文件中包含已存在的人员编号", r,
           note="见 G4：错误消息没带部门，但撞的是 D2 的编号，构成跨部门存在性侧信道")

    # TC-XLS-14：1 行合法 + 1 行非法 → 整份回滚
    good = row("t1", D1N)
    bad = ("XLS-BAD-%d" % EPOCH, D1N, "坏身份证", "139%08d" % (EPOCH % 100000000), "123456")
    r = imp(b1, [good, bad])
    after = get("/api/gate-persons?keyword=" + good[0], token=s1)
    record("TC-XLS-14", "B1", "400,msg:身份证号必须为 18 位", r,
           data={"合法行是否落库": (after.data or {}).get("total")},
           note="事务回滚：合法那行也不能落库",
           pred=lambda: (after.data or {}).get("total") == 0)

    # TC-XLS-16：会话里的工作部门已脱节（B1 的会话被切到不在管理范围内的 D2）
    rd.set_working_department(b1, D2)
    r = imp(b1, [row("s1", None)])
    record("TC-XLS-16", "B1", "400,msg:当前工作部门不在你的管理范围内，请切换工作部门后再导入", r,
           note="会话里的工作部门可能已脱节，必须有第二道防线")
    rd.set_working_department(b1, D1)
    back = get("/api/auth/session", token=b1)
    print("B1 工作部门已复位:", back.data.get("working_department_id"))

    save_tokens(TK)
    dump_results("results_xls_import.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
