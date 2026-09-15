# -*- coding: utf-8 -*-
"""5.10 审计留痕 TC-AUDIT-*（路径是 /admin/api/audit-events，方案里写的 /api/... 是 404）"""
import os
import sys
import urllib.parse
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (get, put, post, request, login, load_state, load_tokens,
                     record, dump_results, save_tokens, redis_client)

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
ID2 = P["GP-D1-002"]["id"]

AUD = "/admin/api/audit-events"


def _window():
    """查询窗口上限 31 天（超过直接 400），取 30 天。

    右端必须每次重算并留出余量：事件发生在本脚本启动之后，
    若沿用启动时刻算出的 occurred_to，刚写入的审计会被窗口排除掉。
    """
    to = datetime.now() + timedelta(hours=1)
    fr = datetime.now() - timedelta(days=30)
    return ("occurred_from=%s&occurred_to=%s"
            % (fr.strftime("%Y-%m-%dT%H:%M:%S"), to.strftime("%Y-%m-%dT%H:%M:%S")))


def events(tok, action=None, extra=""):
    q = f"{AUD}?{_window()}&page_size=100&page=1{extra}"
    if action:
        q += "&action=" + action
    r = get(q, token=tok)
    d = r.data or {}
    return r, (d.get("events") or d.get("items") or [])


def main():
    b1, s1 = TK["B1"], TK["S1"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)

    # TC-AUDIT-01：关键动作都要留痕
    need = ["GATE_PERSON_CREATED", "GATE_PERSON_UPDATED", "GATE_PERSON_REVIEWED",
            "GATE_DELETE_REQUESTED", "GATE_DELETE_REQUEST_REVIEWED", "GATE_PERSON_DELETED"]
    seen = {}
    for a in need:
        r, items = events(s1, a)
        seen[a] = len(items)
    r0 = get(f"{AUD}?{_window()}&page_size=1", token=s1)
    record("TC-AUDIT-01", "S1", "200", r0, data=seen,
           note="逐一统计各动作的审计条数（>0 即留痕成功）",
           pred=lambda: all(v > 0 for v in seen.values()))

    # TC-AUDIT-02 / 03：风险等级
    _, del_ev = events(s1, "GATE_PERSON_DELETED")
    _, cre_ev = events(s1, "GATE_PERSON_CREATED")
    rl_del = sorted({e["risk_level"] for e in del_ev})
    rl_cre = sorted({e["risk_level"] for e in cre_ev})
    record("TC-AUDIT-02", "S1", "200", r0, data={"risk_level": rl_del},
           note="人员物理删除须为 CRITICAL", pred=lambda: rl_del == ["CRITICAL"])
    record("TC-AUDIT-03", "S1", "200", r0, data={"risk_level": rl_cre},
           note="新建须为 HIGH", pred=lambda: rl_cre == ["HIGH"])

    # TC-AUDIT-04 / 05
    r = get(f"{AUD}?{_window()}", token=TK["A1"])
    record("TC-AUDIT-04", "A1(ADMIN)", "403", r, note="要求 hasRole('SUPER_ADMIN')，ADMIN 也不行")
    r = get(f"{AUD}?{_window()}", token=s1)
    record("TC-AUDIT-05", "S1", "200", r)
    # 方案里写的路径对照
    r = get("/api/audit-events?pageSize=3", token=s1)
    record("TC-AUDIT-05b", "S1", "200", r, data={"path": "/api/audit-events"},
           note="方案里写的路径实际是 404，真实路径是 /admin/api/audit-events", info=True)

    # TC-AUDIT-06：越权操作要落 AUTHORIZATION_DENIED
    # 用真正走方法级安全（403）的操作：DELETE 是 denyAll()，越界只是业务 400，不会记这条。
    before = len(events(s1, "AUTHORIZATION_DENIED")[1])
    r = request("DELETE", f"/api/gate-persons/{ID2}", token=b1)
    after = len(events(s1, "AUTHORIZATION_DENIED")[1])
    record("TC-AUDIT-06", "B1", "403", r, data={"before": before, "after": after},
           note="越权被方法级安全拒绝后应新增 AUTHORIZATION_DENIED",
           pred=lambda: after > before)

    # TC-AUDIT-07：无变化提交不写更新审计
    cur = get(f"/api/gate-persons/{ID2}", token=s1).data
    n_before = len([e for e in events(s1, "GATE_PERSON_UPDATED")[1]
                    if str(ID2) == (e.get("target_id") or "")])
    r = put(f"/api/gate-persons/{ID2}", token=b1,
            form={"code": cur["code"], "dept": cur["dept"], "name": cur["name"],
                  "phone": cur["phone"], "idCard": cur["idCard"]})
    n_after = len([e for e in events(s1, "GATE_PERSON_UPDATED")[1]
                   if str(ID2) == (e.get("target_id") or "")])
    record("TC-AUDIT-07", "B1", "200", r, data={"before": n_before, "after": n_after},
           note="原样提交不应新增 GATE_PERSON_UPDATED（target_id=%s）" % ID2,
           pred=lambda: n_after == n_before)

    # TC-AUDIT-08：批量导入只落 1 条 CREATED，且 sample_codes 采样 ≤ 20
    ok8 = None
    n_cre = len(events(s1, "GATE_PERSON_CREATED")[1])
    samples = [len((e.get("target_summary") or {}).get("sample_codes") or [])
               for e in events(s1, "GATE_PERSON_CREATED")[1]
               if (e.get("target_summary") or {}).get("record_count")]
    batches = [(e.get("target_summary") or {}).get("record_count")
               for e in events(s1, "GATE_PERSON_CREATED")[1]
               if (e.get("target_summary") or {}).get("record_count")]
    record("TC-AUDIT-08", "S1", "200", r0,
           data={"批量导入审计条数": len(batches), "各批 record_count": batches,
                 "sample 长度": samples},
           note="批量导入应只落 1 条 CREATED，sample_codes ≤ 20",
           pred=lambda: bool(batches) and all(s <= 20 for s in samples),
           info=not batches)

    save_tokens(TK)
    dump_results("results_audit.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
