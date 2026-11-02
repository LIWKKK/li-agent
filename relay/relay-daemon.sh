#!/usr/bin/env bash
# LI Agent 中继守护脚本 —— start | stop | restart | status | logs
#
# 设计要点：
#   - 所有可变值走环境变量（RELAY_PORT / DSH_HOME / LI_AGENT_PUBLIC_URL），脚本本身不含机器特定值
#   - 用 setsid + nohup 脱离终端，SSH 断开不影响
#   - PID 文件放 TMPDIR，重启机器后自然失效（不会误判僵尸 PID）
#   - status 做真实 HTTP 健康检查，不只看进程在不在
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${TMPDIR:-/tmp}/li-agent-relay.pid"
LOG_FILE="${RELAY_LOG:-${DIR}/relay.log}"
PORT="${RELAY_PORT:-19490}"
DSH_PORT="${DSH_PORT:-3080}"
NODE_BIN="${NODE_BIN:-node}"

alive() {
  [ -f "$PID_FILE" ] || return 1
  local pid; pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" 2>/dev/null
}

health() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
    "http://127.0.0.1:${PORT}/api/status" 2>/dev/null)" || true
  echo "${code:-000}"
}

start() {
  if alive; then
    echo "已在运行（pid $(cat "$PID_FILE")，端口 $PORT）"
    return 0
  fi
  rm -f "$PID_FILE"
  cd "$DIR" || return 1

  # 用 env 数组传变量：${VAR:+VAR="x"} 会把引号当字面量，env 赋值必失败
  local envargs=(RELAY_PORT="$PORT" DSH_PORT="$DSH_PORT")
  [ -n "${DSH_HOME:-}" ] && envargs+=(DSH_HOME="$DSH_HOME")
  [ -n "${LI_AGENT_PUBLIC_URL:-}" ] && envargs+=(LI_AGENT_PUBLIC_URL="$LI_AGENT_PUBLIC_URL")

  setsid nohup env "${envargs[@]}" "$NODE_BIN" src/server.mjs >>"$LOG_FILE" 2>&1 < /dev/null &
  local pid=$!
  echo "$pid" > "$PID_FILE"

  # 最多等 8 秒，看健康检查能不能过
  local i
  for i in $(seq 1 16); do
    sleep 0.5
    if [ "$(health)" = "200" ]; then
      echo "已启动（pid $pid，端口 $PORT）"
      grep -E '配对码|监听' "$LOG_FILE" 2>/dev/null | tail -4
      return 0
    fi
  done

  if alive; then
    echo "进程在跑，但健康检查未通过（看日志：$LOG_FILE）" >&2
  else
    echo "启动失败，日志尾部：" >&2
  fi
  tail -10 "$LOG_FILE" 2>/dev/null >&2
  return 1
}

stop() {
  if ! alive; then
    echo "未运行"
    rm -f "$PID_FILE"
    return 0
  fi
  local pid; pid="$(cat "$PID_FILE")"
  kill "$pid" 2>/dev/null
  local i
  for i in $(seq 1 10); do
    sleep 0.4
    kill -0 "$pid" 2>/dev/null || break
  done
  kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
  rm -f "$PID_FILE"
  echo "已停止"
}

status() {
  if alive; then
    local pid; pid="$(cat "$PID_FILE")"
    local code; code="$(health)"
    echo "运行中（pid $pid，端口 $PORT，健康检查 HTTP $code）"
    [ "$code" = "200" ] && return 0 || return 1
  fi
  echo "未运行"
  return 1
}

logs() {
  tail -n "${2:-40}" "$LOG_FILE" 2>/dev/null || echo "(无日志：$LOG_FILE)"
}

case "${1:-status}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  logs)    logs "$@" ;;
  *)
    echo "用法：$0 {start|stop|restart|status|logs [行数]}" >&2
    exit 2
    ;;
esac