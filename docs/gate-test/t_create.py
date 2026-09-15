# -*- coding: utf-8 -*-
"""5.3 单条录入 TC-CREATE-*"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import (post, login, load_state, load_tokens, record, dump_results,
                     save_tokens, save_state, redis_client)
from prep import mkface

ST = load_state("state.json")
TK = load_tokens()
P = load_state("persons.json")
D1, D2 = ST["D1"], ST["D2"]
D1N, D2N = ST["D1_NAME"], ST["D2_NAME"]
D1_ID = P["GP-D1-001"]["id"]


def create(tok, code, dept, name, phone, id_card, face=None, filename="face.png",
           mime="image/png"):
    files = [( "face", filename, face, mime)] if face is not None else None
    return post("/api/gate-persons", token=tok,
                form={"code": code, "dept": dept, "name": name,
                      "phone": phone, "idCard": id_card},
                files=files)


def main():
    b1, b2, s1 = TK["B1"], TK["B2"], TK["S1"]
    a2, _ = login("t_admin", "Test@2026x")
    TK["A2"] = a2
    redis_client().set_working_department(a2, D1)
    png = mkface()
    out = {}

    # TC-CREATE-01
    r = create(b1, "GP-D1-010", D1N, "门禁测试-新录", "13000000301",
               "350102199001010101", png)
    if r.data:
        out["GP-D1-010"] = r.data
    record("TC-CREATE-01", "B1", "201", r,
           data={"approveStatus": (r.data or {}).get("approveStatus"),
                 "syncStatus": (r.data or {}).get("syncStatus")},
           note="新录入一律待审核 / 未同步",
           pred=lambda: (r.data or {}).get("approveStatus") == "审核中"
           and (r.data or {}).get("syncStatus") == "未同步")

    # TC-CREATE-02
    r = create(s1, "GP-G-001", D2N, "门禁测试-超管建", "13000000302",
               "350102199001010102", png)
    if r.data:
        out["GP-G-001"] = r.data
    record("TC-CREATE-02", "S1", "201", r, note="全局角色可写任意部门")

    # TC-CREATE-03/04/05/06 越界写入
    for tc, who, tok, dept, note in [
        ("TC-CREATE-03", "B1", b1, D2N, "B1 往 D2 写"),
        ("TC-CREATE-04", "B2", b2, D1N, "B2 往 D1 写"),
        ("TC-CREATE-05", "B1", b1, "这是一个不存在的部门", "解析不出部门编码 → 视为越界"),
        ("TC-CREATE-06", "A2(切 D1)", a2, D2N, "平台管理收窄后同样受限"),
    ]:
        r = create(tok, "GP-ATK-%s" % tc[-2:], dept, "越界测试",
                   "13000000303", "350102199001010103", png)
        record(tc, who, "403", r, note=note)

    # TC-CREATE-07 编号重复：唯一性检查必须先于上传
    r = create(b1, "GP-D1-001", D1N, "重复编号", "13000000304",
               "350102199001010104", png)
    record("TC-CREATE-07", "B1", "400,msg:人员编号已存在", r,
           note="同时核对不会落文件，见 CREATE-07-file 检查")

    # TC-CREATE-08 身份证重复
    r = create(b1, "GP-D1-011", D1N, "重复身份证", "13000000305",
               "350102199001010011", png)
    record("TC-CREATE-08", "B1", "400,msg:身份证号已存在", r)

    # TC-CREATE-09 手机号格式
    r = create(b1, "GP-D1-012", D1N, "手机号错", "abc",
               "350102199001010105", png)
    record("TC-CREATE-09", "B1", "400,msg:手机号格式不正确，只能填写数字", r)

    # TC-CREATE-10 身份证长度
    r = create(b1, "GP-D1-013", D1N, "身份证短", "13000000306",
               "35010219900101", png)
    record("TC-CREATE-10", "B1", "400,msg:身份证号必须为 18 位", r)

    # TC-CREATE-11 不传人脸
    r = create(b1, "GP-D1-014", D1N, "缺人脸", "13000000307",
               "350102199001010106", face=None)
    record("TC-CREATE-11", "B1", "400", r, note="缺少必需 part face")

    # TC-CREATE-12 3MB PNG
    big = b"\x89PNG\r\n\x1a\n" + os.urandom(3 * 1024 * 1024)
    r = create(b1, "GP-D1-015", D1N, "大图", "13000000308",
               "350102199001010107", big)
    record("TC-CREATE-12", "B1", "400,msg:人脸照片不能超过 2MB", r)

    # TC-CREATE-13 纯文本伪造成 png
    r = create(b1, "GP-D1-016", D1N, "伪造png", "13000000309",
               "350102199001010108", "这是一段纯文本，不是图片".encode("utf-8"),
               filename="note.png", mime="image/png")
    record("TC-CREATE-13", "B1", "400,msg:人脸照片内容不是受支持的图片", r,
           note="魔数校验；最容易被绕过的一层")

    # TC-CREATE-14 webp 后缀里塞 WAV
    wav = b"RIFF" + b"\x00\x00\x00\x00" + b"WAVE" + os.urandom(64)
    r = create(b1, "GP-D1-017", D1N, "伪造webp", "13000000310",
               "350102199001010109", wav, filename="a.webp", mime="image/webp")
    record("TC-CREATE-14", "B1", "400,msg:人脸照片内容不是受支持的图片", r,
           note="WebP 需核对偏移 8 处标识")

    # TC-CREATE-15 真实 GIF
    gif = b"GIF89a" + os.urandom(64)
    r = create(b1, "GP-D1-018", D1N, "真GIF", "13000000311",
               "350102199001010110", gif, filename="a.gif", mime="image/gif")
    if r.data:
        out["GP-D1-018"] = r.data
    record("TC-CREATE-15", "B1", "201", r, note="白名单含 gif")

    # TC-CREATE-16 USER 无 manage
    for who in ("U1", "U3"):
        r = create(TK[who], "GP-U-%s" % who, D1N, "越权", "13000000312",
                   "350102199001010111", png)
        record("TC-CREATE-16", who, "403", r)

    save_state("persons_created.json", out)
    save_tokens(TK)
    dump_results("results_create.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
