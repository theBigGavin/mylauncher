// mrd · 手速排行榜入口（里程碑1）
// 由 server/index.cjs 在非托管模式加载(HOSTING !== "1"): 打开排行榜 SQLite(server/data/knock.db,
// gitignored 运行时数据), 生成 /api/v1/knock/* 路由挂载进 routes(只增不改核心路由)。
// 托管版(:3200, HOSTING=1)不加载, 避免双进程写同一 SQLite 库。
// 娱乐榜: 客户端上报即信任 + 轻校验, 不做真防伪; 不存设备标识/IP 映射。
"use strict";
const { openKnockDb, defaultDbPath } = require("./db.cjs");
const { createKnockRoutes } = require("./routes.cjs");

/**
 * 初始化排行榜: 打开 DB + 生成路由表。
 * 返回 routes 供 index.cjs Object.assign 进 routes 对象。
 */
function initKnock() {
  const db = openKnockDb(defaultDbPath());
  const routes = createKnockRoutes(db);
  console.log("[knock] 手速排行榜已启用 — /api/v1/knock/{leaderboard,percentile,submit} (SQLite)");
  return routes;
}

module.exports = { initKnock, defaultDbPath };
