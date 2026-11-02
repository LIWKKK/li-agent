#!/usr/bin/env bash
# LI Agent 中继保活 —— 由 cron 每分钟跑一次。
#
# 判定三件事，任一不满足就拉起：
#   1. 进程在不在（PID 文件 + kill -0）
#   2. 端口在不在听
#   3. /api/status 健不健康（HTTP 200）
#
# 只做"活着"这件事，不管别的；输出到日志，不刷屏。
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DAEMON="$DIR/relay-daemon.sh"
STATE="${RELAY_KEEPALIVE_LOG:-${DIR}/keepalive.log}"
PORT="${RELAY_PORT:-19490}"

# 从 .env 读配置（deploy.sh 生成）
ENV_FILE="$DIR/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  PORT="${RELAY_PORT:-$PORT}"
fi

log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$STATE"; }

healthy() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 \
    "http://127.0.0.1:$PORT/api/status" 2>/dev/null)" || true
  [ "${code:-000}" = "200" ]
}

# 日志体积控制：超过 512KB 就截断到最近 500 行
if [ -f "$STATE" ] && [ "$(stat -c %s "$STATE" 2>/dev/null || echo 0)" -gt 524288 ]; then
  tail -n 500 "$STATE" > "$STATE.tmp" && mv "$STATE.tmp" "$STATE"
fi

if healthy; then
  exit 0
fi

log "健康检查失败，尝试拉起中继"
RELAY_PORT="$PORT" RELAY_LOG="${RELAY_LOG:-${DIR}/relay.log}" \
  bash "$DAEMON" start >> "$STATE" 2>&1

sleep 3
if healthy; then
  log "已恢复（端口 $PORT）"
  exit 0
fi

log "拉起失败，需要人工介入（看 relay.log）"
exit 1
