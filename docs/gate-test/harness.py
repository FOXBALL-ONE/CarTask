# -*- coding: utf-8 -*-
"""门禁模块真机测试的低层 HTTP 封装。

用 Python 而不是 curl 的原因：Windows 的 curl 会把 argv 里的中文按 ANSI 代码页
（GBK）编码，后端收到非法 UTF-8 直接 400。Python 全程 UTF-8 可控，
multipart 也自己拼，避免 curl -F 的同样问题。
"""
import base64
import json
import os
import re
import sys
import uuid
import urllib.error
import urllib.request

# Windows 下 Python 默认用 GBK 输出，中文一 print 就炸。
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass

BASE = "http://127.0.0.1:8080"
ART = os.path.dirname(os.path.abspath(__file__))


class HttpResult:
    def __init__(self, status, body, headers=None, raw_bytes=None):
        self.status = status
        self.raw = body
        self.body_bytes = raw_bytes if raw_bytes is not None else body.encode("utf-8", "replace")
        self.headers = headers or {}
        try:
            self.json = json.loads(body)
        except Exception:
            self.json = None

    @property
    def message(self):
        if isinstance(self.json, dict):
            return self.json.get("message")
        return None

    @property
    def data(self):
        if isinstance(self.json, dict):
            return self.json.get("data")
        return None

    @property
    def api_status(self):
        if isinstance(self.json, dict):
            return self.json.get("status")
        return None

    def __repr__(self):
        return f"<{self.status} {self.message!r}>"


def request(method, path, token=None, json_body=None, form=None, files=None,
            raw_body=None, content_type=None, timeout=60):
    """files: [(field, filename, content_bytes, mime), ...]"""
    url = BASE + path
    headers = {}
    data = None

    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    elif raw_body is not None:
        data = raw_body if isinstance(raw_body, bytes) else raw_body.encode("utf-8")
        if content_type:
            headers["Content-Type"] = content_type
    elif form is not None or files is not None:
        boundary = "----carTaskTest" + uuid.uuid4().hex
        buf = []
        for k, v in (form or {}).items():
            buf.append(("--" + boundary + "\r\n").encode())
            buf.append(
                (f'Content-Disposition: form-data; name="{k}"\r\n\r\n').encode("utf-8"))
            buf.append((str(v)).encode("utf-8") if isinstance(v, str) else str(v).encode())
            buf.append(b"\r\n")
        for field, filename, content, mime in (files or []):
            buf.append(("--" + boundary + "\r\n").encode())
            buf.append(
                (f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
                 f"Content-Type: {mime}\r\n\r\n").encode("utf-8"))
            buf.append(content)
            buf.append(b"\r\n")
        buf.append(("--" + boundary + "--\r\n").encode())
        data = b"".join(buf)
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary

    if token:
        headers["Authorization"] = "Bearer " + token

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return HttpResult(resp.status, raw.decode("utf-8", "replace"),
                              dict(resp.headers), raw)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return HttpResult(e.code, raw.decode("utf-8", "replace"), dict(e.headers), raw)
    except Exception as e:  # noqa: BLE001
        return HttpResult(-1, f'{{"transport_error": {json.dumps(str(e))}}}')


def get(p, token=None, **kw):
    return request("GET", p, token=token, **kw)


def post(p, token=None, **kw):
    return request("POST", p, token=token, **kw)


def put(p, token=None, **kw):
    return request("PUT", p, token=token, **kw)


def delete(p, token=None, **kw):
    return request("DELETE", p, token=token, **kw)


# ── 认证 ────────────────────────────────────────────────────────────────

def captcha():
    """返回 (token, code)。SVG 是 base64 明文，<text> 节点里就是要填的数字。"""
    r = get("/api/auth/captcha")
    d = r.data or {}
    svg = base64.b64decode(d["image"].split("base64,", 1)[1]).decode("utf-8")
    code = "".join(re.findall(r">(\d)</text>", svg))
    return d["token"], code


def login(username, password):
    """返回 (token_or_None, HttpResult)。"""
    tok, code = captcha()
    r = post("/api/auth/login", json_body={
        "username": username, "password": password,
        "captchaToken": tok, "captchaAnswer": code})
    if r.status == 200 and r.data:
        return r.data.get("access_token"), r
    return None, r


def sms_login(phone, code="000000"):
    r = post("/api/auth/sms/login", json_body={"phone": phone, "code": code})
    if r.status == 200 and r.data:
        return r.data.get("access_token"), r
    return None, r


def session(token):
    return get("/api/auth/session", token=token)


# ── 凭据仓库（跨脚本共享，避免反复登录触发限流）────────────────────────

TOKENS_PATH = os.path.join(ART, "tokens.json")


def save_tokens(d):
    with open(TOKENS_PATH, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)


def load_tokens():
    if os.path.exists(TOKENS_PATH):
        with open(TOKENS_PATH, encoding="utf-8") as f:
            return json.load(f)
    return {}


def load_state(name, default=None):
    p = os.path.join(ART, name)
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    return default


def save_state(name, obj):
    p = os.path.join(ART, name)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


# ── 断言与记录 ──────────────────────────────────────────────────────────

RESULTS = []


def record(tc, actor, expect, result, data=None, note="", pred=None, info=False):
    """info=True 表示这条只记录观察到的行为（刻画性用例），不参与通过/失败判定。"""
    ok = expected_ok(expect, result)
    if ok and pred is not None:
        try:
            ok = bool(pred())
        except Exception as e:  # noqa: BLE001
            ok = False
            note = (note + f" [pred 异常: {e}]").strip()
    verdict = "INFO" if info else ("PASS" if ok else "FAIL")
    RESULTS.append({
        "id": tc, "actor": actor, "expect": expect,
        "http": result.status, "api_status": result.api_status,
        "message": result.message, "data": data, "note": note,
        "verdict": verdict,
    })
    print(f"[{verdict}] {tc} ({actor}) http={result.status} "
          f"msg={result.message!r} {note}")
    return ok


def expected_ok(expect, result):
    """expect 形如 '200' / '400' / '403' / '404' / '201' / '500' / '*'
    或用 'msg:xxx' 形式表达期望 message 包含的内容，逗号分隔多条件。"""
    if expect == "*":
        return True
    parts = [p.strip() for p in str(expect).split(",") if p.strip()]
    for p in parts:
        if p.startswith("oneof:"):
            if str(result.status) not in [x.strip() for x in p[6:].split("|")]:
                return False
        elif p.startswith("msg:"):
            frag = p[4:]
            if not result.message or frag not in result.message:
                return False
        elif p.startswith("notmsg:"):
            frag = p[7:]
            if result.message and frag in result.message:
                return False
        elif p.startswith("api:"):
            if str(result.api_status) != p[4:]:
                return False
        else:
            if str(result.status) != p:
                return False
    return True


def dump_results(path=None):
    path = path or os.path.join(ART, "results.json")
    save_state(os.path.basename(path), RESULTS)
    print(f"\n=== {len(RESULTS)} cases, "
          f"{sum(1 for r in RESULTS if r['verdict']=='PASS')} PASS, "
          f"{sum(1 for r in RESULTS if r['verdict']=='FAIL')} FAIL ===")
    for r in RESULTS:
        if r["verdict"] == "FAIL":
            print(f"  FAIL {r['id']}: expect={r['expect']} actual http={r['http']} "
                  f"api={r['api_status']} msg={r['message']!r} data={r['data']}")


# ── Redis 直连（仅用于绕过 F1 缺陷，取得「会话已切换」的等价状态）────────

import socket as _socket


class _Redis:
    def __init__(self):
        env = {}
        root = os.path.dirname(os.path.dirname(ART))  # 仓库根
        for line in open(os.path.join(root, ".env"), encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
        self.s = _socket.create_connection((env["REDIS_HOST"], int(env["REDIS_PORT"])), 5)
        self._cmd("AUTH", env.get("REDIS_PASSWORD", ""))
        self._cmd("SELECT", "5")

    def _resp(self):
        line = b""
        while not line.endswith(b"\r\n"):
            line += self.s.recv(1)
        t, line = line[:1], line[1:-2]
        if t == b"+":
            return line.decode()
        if t == b"-":
            return "ERR " + line.decode()
        if t == b":":
            return int(line)
        if t == b"$":
            n = int(line)
            if n == -1:
                return None
            buf = b""
            while len(buf) < n + 2:
                buf += self.s.recv(n + 2 - len(buf))
            return buf[:n].decode("utf-8", "replace")
        if t == b"*":
            return [self._resp() for _ in range(int(line))]
        return None

    def _cmd(self, *args):
        out = b"*%d\r\n" % len(args)
        for a in args:
            b = a if isinstance(a, bytes) else str(a).encode()
            out += b"$%d\r\n%s\r\n" % (len(b), b)
        self.s.sendall(out)
        return self._resp()

    def jti(self, token):
        return json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "=="))["jti"]

    def get_session(self, token):
        return json.loads(self._cmd("GET", "shopmall:auth:jwt:" + self.jti(token)))

    def set_working_department(self, token, department_id):
        """把会话里的工作部门直接改掉。

        F1 缺陷导致 PUT /api/auth/working-department 写不进 Redis，而下游用例
        （A2/B1 的部门范围）必须先把会话切到目标部门。这里手工写入与
        replaceWorkingDepartment 成功路径完全相同的字段。
        """
        key = "shopmall:auth:jwt:" + self.jti(token)
        sess = json.loads(self._cmd("GET", key))
        sess["working_department_id"] = department_id
        ttl = self._cmd("TTL", key)
        self._cmd("SET", key, json.dumps(sess, ensure_ascii=False, separators=(",", ":")),
                  "EX", str(max(ttl, 60)))
        return json.loads(self._cmd("GET", key)).get("working_department_id")

    def close(self):
        self.s.close()


def redis_client():
    return _Redis()

