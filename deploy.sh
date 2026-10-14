#!/usr/bin/env bash
set -Eeuo pipefail

readonly WEB_DIR="/root/CarTask/web"
readonly SERVER_DIR="/root/CarTask/server"
readonly FRONTEND_URL="https://cnb.cool/FOXBALL-ONE/CarTask/-/releases/download/latest/frontend.tar.gz"
readonly BACKEND_URL="https://cnb.cool/FOXBALL-ONE/CarTask/-/releases/download/latest/backend.jar"

STAGE_DIR=""
BACKEND_TMP=""
trap '[[ -z "$STAGE_DIR" ]] || rm -rf -- "$STAGE_DIR"; [[ -z "$BACKEND_TMP" ]] || rm -f -- "$BACKEND_TMP"' EXIT

if [[ "$(id -u)" -ne 0 ]]; then
  echo "请以 root 用户运行此脚本。" >&2
  exit 1
fi

for command in curl tar systemctl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "缺少必要命令：$command" >&2
    exit 1
  fi
done

if [[ ! -d "$WEB_DIR" || ! -d "$SERVER_DIR" ]]; then
  echo "部署目录不存在：$WEB_DIR 或 $SERVER_DIR" >&2
  exit 1
fi

rm -rf -- "$WEB_DIR/frontend"
STAGE_DIR="$(mktemp -d "$WEB_DIR/.frontend-deploy.XXXXXX")"
curl --fail --location --retry 3 --retry-delay 2 "$FRONTEND_URL" --output "$STAGE_DIR/frontend.tar.gz"
tar --extract --gzip --file "$STAGE_DIR/frontend.tar.gz" --directory "$STAGE_DIR"

if [[ ! -d "$STAGE_DIR/.output" ]]; then
  echo "前端归档中未找到 .output 目录。" >&2
  exit 1
fi

mv -- "$STAGE_DIR/.output" "$STAGE_DIR/frontend"
mv -- "$STAGE_DIR/frontend" "$WEB_DIR/frontend"
systemctl restart frontend.service

rm -f -- "$SERVER_DIR/backedn.jar"
BACKEND_TMP="$(mktemp "$SERVER_DIR/.backend.jar.XXXXXX")"
curl --fail --location --retry 3 --retry-delay 2 "$BACKEND_URL" --output "$BACKEND_TMP"
if [[ ! -s "$BACKEND_TMP" ]]; then
  echo "后端下载文件为空。" >&2
  exit 1
fi

chmod 0644 "$BACKEND_TMP"
mv -f -- "$BACKEND_TMP" "$SERVER_DIR/backend.jar"
BACKEND_TMP=""
systemctl restart backend.service

echo "前端和后端部署完成。"
