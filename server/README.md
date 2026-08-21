# mylauncher server — 手速排行榜（knock）独立进程

mylauncher 功德木鱼「最快手速」全球榜的后端服务，监听本机 `:3032`，公网入口
`https://hermes.cc.cd/api/v1/knock`（cloudflared ingress → localhost:3032）。

## 历史溯源

- **源仓库**: [theBigGavin/marketingdashboard](https://github.com/theBigGavin/marketingdashboard)（mrd）
- **源 commit**: `a5f262e`（迁入时 mrd 仓 HEAD；源路径 `server/knock-standalone.cjs`、`server/knock/`、`server/knock.test.cjs`、`server/start_knock.sh`）
- **迁移时刻**: 2026-08-22（P0-3a 架构拆分：knock 归属 mylauncher 产品线，mrd 仓仅保留 `/api/v1/knock/*` 过渡重定向）
- **数据文件**: `server/data/knock.db`（SQLite/WAL）随进程整体迁移，内容未做任何改动；git 历史可用 `git log --follow -- server/knock-standalone.cjs` 在源仓库追溯

## 运行

由 pm2 守护（进程名 `knock`）：

```bash
pm2 start bash --name knock --cwd /home/gavin/hermes_space/mylauncher -- -c "node server/knock-standalone.cjs"
pm2 save
```

统一入口脚本: `bash server/start_knock.sh {start|stop|restart|status}`（内部转 pm2 操作, start 自动带 save; 无裸进程路径）

## 路由

- `GET  /api/v1/knock/leaderboard` — 全球榜
- `GET  /api/v1/knock/percentile` — 百分位
- `POST /api/v1/knock/submit` — 成绩上报
- `POST /api/v1/knock/track` / `POST /api/v1/knock/feedback` — UTM/反馈埋点计数
- `/` → 302 到官网；`/go/*` → 302 保留路径到 www；其余一律 404

## 测试

```bash
node --test server/knock.test.cjs   # 需 Node ≥ 22（node:sqlite）
```

## 数据消费者（只读 knock.db，路径硬编码，迁库须同步）

- mrd 仓 `server/sources/acquisition.cjs`（/api/acquisition 引流面板）
- opc-os `opc-api/sources/acquisition.cjs`（OPC 透明办公室同款面板）

隐私红线：只记维度计数，不采 IP/UA/referrer。
