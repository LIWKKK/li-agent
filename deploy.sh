#!/usr/bin/env bash
# LI Agent 一键部署 —— 装中继、生配置、起服务、加守护
#
# 用法：
#   ./deploy.sh                       # 交互式部署（推荐首次）
#   ./deploy.sh --port 19490 --yes    # 无人值守
#   ./deploy.sh --status              # 只看状态
#   ./deploy.sh --stop                # 停服务
#   ./deploy.sh --uninstall           # 卸掉（保留配置）
#
# 依赖：node >= 20、curl、bash。中继零 npm 依赖，不需要 npm install。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELAY_DIR="$HERE/relay"
DAEMON="$RELAY_DIR/relay-daemon.sh"
STATE_DIR="$RELAY_DIR/state"
ENV_FILE="$RELAY_DIR/.env"

PORT="${RELAY_PORT:-19490}"
DSH_PORT="${DSH_PORT:-3080}"
DSH_HOME_DEFAULT="${DSH_HOME:-$HOME/.dsh}"
ASSUME_YES=0
ACTION="deploy"

c_red()   { printf '\033[31m%s\033[0m\n' "$*"; }
c_green() { printf '\033[32m%s\033[0m\n' "$*"; }
c_dim()   { printf '\033[2m%s\033[0m\n' "$*"; }
c_bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --port)      PORT="$2"; shift 2 ;;
    --dsh-port)  DSH_PORT="$2"; shift 2 ;;
    --dsh-home)  DSH_HOME_DEFAULT="$2"; shift 2 ;;
    --yes|-y)    ASSUME_YES=1; shift ;;
    --status)    ACTION="status"; shift ;;
    --stop)      ACTION="stop"; shift ;;
    --restart)   ACTION="restart"; shift ;;
    --uninstall) ACTION="uninstall"; shift ;;
    --help|-h)
      sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) c_red "未知参数：$1（--help 看用法）"; exit 2 ;;
  esac
done

# ---------- 前置检查 ----------
check_deps() {
  local missing=0
  command -v node >/dev/null 2>&1 || { c_red "缺 node（需要 >= 20）"; missing=1; }
  command -v curl >/dev/null 2>&1 || { c_red "缺 curl"; missing=1; }
  if [ "$missing" = "1" ]; then
    c_dim "Debian/Ubuntu：apt install -y nodejs curl（node 建议用 nvm 装 20+）"
    exit 1
  fi
  local major
  major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  if [ "${major:-0}" -lt 20 ]; then
    c_red "node 版本过低（当前 $(node -v)，需要 >= 20）"
    exit 1
  fi
  c_green "✓ node $(node -v) / curl 就绪"
}

# ---------- 生成 .env ----------
write_env() {
  cat > "$ENV_FILE" <<EOF
# LI Agent 中继配置 —— 由 deploy.sh 生成，改完 restart 生效
RELAY_PORT=$PORT
DSH_PORT=$DSH_PORT
DSH_HOME=$DSH_HOME_DEFAULT
EOF
  chmod 600 "$ENV_FILE"
  c_green "✓ 配置写入 $ENV_FILE"
}

load_env() {
  [ -f "$ENV_FILE" ] || return 0
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  PORT="${RELAY_PORT:-$PORT}"
  DSH_PORT="${DSH_PORT:-$DSH_PORT}"
}

# ---------- 拉中继依赖（零 npm 依赖，只校验文件齐不齐） ----------
verify_relay() {
  local need="src/server.mjs src/relay.mjs src/deepseek.mjs src/dshauth.mjs src/dshlink.mjs src/normalize.mjs src/pow.mjs src/scanusage.mjs package.json"
  local f
  for f in $need; do
    [ -f "$RELAY_DIR/$f" ] || { c_red "缺文件：relay/$f"; exit 1; }
  done
  mkdir -p "$STATE_DIR"
  c_green "✓ 中继源码完整"
}

# ---------- 自检：起服务后打一次健康检查 ----------
self_check() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 "http://127.0.0.1:$PORT/api/status" 2>/dev/null)" || true
  code="${code:-000}"
  if [ "$code" = "200" ]; then
    c_green "✓ 健康检查通过（http://127.0.0.1:$PORT/api/status）"
    local info
    info="$(curl -s --max-time 4 "http://127.0.0.1:$PORT/api/status" 2>/dev/null)"
    local pairing
    pairing="$(printf '%s' "$info" | sed -n 's/.*"pairingCode":"\([^"]*\)".*/\1/p')"
    [ -n "$pairing" ] && c_bold "  配对码：$pairing（手表配对时输入）"
    return 0
  fi
  c_red "✗ 健康检查失败（HTTP $code）"
  return 1
}

# ---------- 装机自启（用户级 systemd，无 root 也能用） ----------
install_autostart() {
  local unit_dir="$HOME/.config/systemd/user"
  if ! command -v systemctl >/dev/null 2>&1; then
    c_dim "  无 systemd，跳过自启（用 cron 或手动起）"
    return 0
  fi
  mkdir -p "$unit_dir"
  cat > "$unit_dir/li-agent-relay.service" <<EOF
[Unit]
Description=LI Agent Relay (watch bridge)
After=network-online.target

[Service]
Type=simple
WorkingDirectory=$RELAY_DIR
EnvironmentFile=$ENV_FILE
ExecStart=$(command -v node) src/server.mjs
Restart=always
RestartSec=5
StandardOutput=append:$RELAY_DIR/relay.log
StandardError=append:$RELAY_DIR/relay.log

[Install]
WantedBy=default.target
EOF
  systemctl --user daemon-reload 2>/dev/null
  systemctl --user enable --now li-agent-relay 2>/dev/null
  if systemctl --user is-active --quiet li-agent-relay 2>/dev/null; then
    c_green "✓ 已装用户级 systemd 自启（li-agent-relay.service）"
    c_dim "  开机自启需登录一次：loginctl enable-linger $(whoami)"
  else
    c_dim "  systemd 用户实例不可用，改用 nohup 守护（restart 需手动 ./deploy.sh --restart）"
    return 0
  fi
}

# ---------- 动作 ----------
do_deploy() {
  c_bold "== LI Agent 中继部署 =="
  check_deps
  load_env
  verify_relay

  if [ "$ASSUME_YES" = "0" ]; then
    printf '中继端口 [%s]: ' "$PORT"; read -r v; [ -n "${v:-}" ] && PORT="$v"
    printf 'DSH 端口 [%s]: ' "$DSH_PORT"; read -r v; [ -n "${v:-}" ] && DSH_PORT="$v"
    printf 'DSH_HOME [%s]: ' "$DSH_HOME_DEFAULT"; read -r v; [ -n "${v:-}" ] && DSH_HOME_DEFAULT="$v"
  fi

  write_env
  load_env

  c_dim "启动中继…"
  RELAY_PORT="$PORT" DSH_PORT="$DSH_PORT" DSH_HOME="$DSH_HOME_DEFAULT" \
    RELAY_LOG="$RELAY_DIR/relay.log" bash "$DAEMON" restart

  self_check || { c_red "部署失败，看日志：$RELAY_DIR/relay.log"; exit 1; }
  install_autostart

  c_bold ""
  c_green "== 部署完成 =="
  c_dim "局域网地址：http://$(hostname -I 2>/dev/null | awk '{print $1}'):$PORT"
  c_dim "常用命令：./deploy.sh --status | --restart | --stop | relay/relay-daemon.sh logs"
}

case "$ACTION" in
  deploy)    do_deploy ;;
  status)
    load_env
    if command -v systemctl >/dev/null 2>&1 && systemctl --user is-active --quiet li-agent-relay 2>/dev/null; then
      c_green "systemd 服务：运行中"
    fi
    RELAY_PORT="$PORT" bash "$DAEMON" status ;;
  restart)
    load_env
    if command -v systemctl >/dev/null 2>&1 && systemctl --user is-active --quiet li-agent-relay 2>/dev/null; then
      systemctl --user restart li-agent-relay && c_green "已重启（systemd）"
    else
      RELAY_PORT="$PORT" DSH_PORT="$DSH_PORT" bash "$DAEMON" restart
    fi
    load_env; self_check ;;
  stop)
    if command -v systemctl >/dev/null 2>&1 && systemctl --user is-active --quiet li-agent-relay 2>/dev/null; then
      systemctl --user stop li-agent-relay && c_green "已停止（systemd）"
    fi
    bash "$DAEMON" stop ;;
  uninstall)
    load_env
    if command -v systemctl >/dev/null 2>&1; then
      systemctl --user disable --now li-agent-relay 2>/dev/null
      rm -f "$HOME/.config/systemd/user/li-agent-relay.service"
      systemctl --user daemon-reload 2>/dev/null
    fi
    bash "$DAEMON" stop
    c_green "已卸载服务（配置 $ENV_FILE 和源码保留）"
    c_dim "要彻底清掉：rm -rf $RELAY_DIR" ;;
esac