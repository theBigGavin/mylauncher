#!/usr/bin/env bash
# 手速排行榜独立进程启动脚本(0819-i → 0822 P0-3a 迁入 mylauncher 仓后改版):
# 生产由 pm2 守护(红线: 禁裸进程), start 自动带 pm2 save 固化开机自启。
# 用法: bash server/start_knock.sh {start|stop|restart|status}
set -u
cd "$(dirname "$0")/.." || exit 1

PM2="pm2"
NAME="knock"
PORT="${KNOCK_PORT:-3032}"

is_pm2() { "$PM2" list 2>/dev/null | grep -qw "$NAME"; }

cmd_start() {
  if is_pm2; then
    echo "[knock] pm2 已在守护进程 $NAME (端口=${PORT}); 需要重启请用: $0 restart"
    exit 0
  fi
  # pm2 守护 + save(红线): 与 README「运行」章节命令一致, cwd 为本仓根
  "$PM2" start bash --name "$NAME" --cwd "$PWD" -- -c "node server/knock-standalone.cjs" || exit 1
  "$PM2" save || exit 1
  sleep 1
  if is_pm2; then
    echo "[knock] pm2 已守护 $NAME (端口=${PORT}) 并已 save 固化"
  else
    echo "[knock] 启动失败, 最近日志:"; "$PM2" logs "$NAME" --lines 20 --nostream 2>/dev/null || true
    exit 1
  fi
}

cmd_stop()   { "$PM2" stop "$NAME"; }
cmd_restart(){ "$PM2" restart "$NAME"; }

cmd_status() {
  if is_pm2; then
    "$PM2" describe "$NAME" 2>/dev/null | grep -E "status|script args|exec cwd|uptime|restarts" | head -10
    echo "[knock] 日志: pm2 logs $NAME --lines 20"
  else
    echo "[knock] pm2 未守护 $NAME (端口=${PORT} 未监听)"; exit 1
  fi
}

case "${1:-}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_restart ;;
  status)  cmd_status ;;
  *) echo "用法: $0 {start|stop|restart|status}"; exit 2 ;;
esac
