// mylauncher · 手速排行榜库（SQLite，node:sqlite 内置，零第三方依赖; 0822 P0-3a 自 mrd 仓迁入）
// 木鱼彩蛋「最快手速」全球榜（娱乐榜: 客户端上报即信任, 轻校验, 不做真防伪）。
// 数据模型(只存这些字段, 不存设备标识/IP 映射):
//   nickname  string  ≤16 字符去空白; 空则服务端生成「木鱼玩家#XXXX」
//   rate      float   次/秒 = 1000/最快连击间隔(ms), 保留 1 位小数
//   gap_ms    int     原始间隔(与 rate 互验)
//   samples   int     连击样本数(轻校验)
//   region    string  可选国家/地区码(留空=不采集, 存 NULL)
//   created_at int    服务端时间戳(ms)
// 同 nickname 只保留最好成绩(rate 更高才更新) — nickname UNIQUE + 条件 upsert。
"use strict";
const fs = require("fs");
const path = require("path");

const { DatabaseSync } = require("node:sqlite");

const SCHEMA_VERSION = 3;

/** 打开(必要时创建)排行榜库并迁移到最新 schema。dbPath 可注入(测试用 :memory:) */
function openKnockDb(dbPath) {
  if (dbPath !== ":memory:") fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec("PRAGMA journal_mode = WAL;");
  db.exec("PRAGMA busy_timeout = 5000;");
  migrate(db);
  return db;
}

function migrate(db) {
  const row = db.prepare("PRAGMA user_version").get();
  const ver = row ? Number(row.user_version) : 0;
  if (ver < 1) {
    db.exec(`
      CREATE TABLE IF NOT EXISTS knock_scores (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        nickname   TEXT    NOT NULL UNIQUE,
        rate       REAL    NOT NULL,
        gap_ms     INTEGER NOT NULL,
        samples    INTEGER NOT NULL,
        region     TEXT,
        created_at INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_knock_rank ON knock_scores(rate DESC, created_at ASC);
    `);
  }
  if (ver < 2) {
    // 榜单页 UTM 引流统计(v1.0.138, 0819-x Gavin 指令): 只记渠道维度, 不采 IP/UA/referrer
    db.exec(`
      CREATE TABLE IF NOT EXISTS utm_visits (
        date     TEXT    NOT NULL,  -- UTC+8 当日 YYYY-MM-DD
        source   TEXT    NOT NULL,  -- utm_source
        medium   TEXT    NOT NULL,  -- utm_medium
        campaign TEXT    NOT NULL,  -- utm_campaign
        count    INTEGER NOT NULL DEFAULT 1,
        PRIMARY KEY (date, source, medium, campaign)
      );
    `);
  }
  if (ver < 3) {
    // 全站悬浮反馈按钮转化统计(0820-fb Gavin 指令): 各页反馈 open/submit 计数。
    // 提交内容复用 /api/assistant(不落这里), 本表只做转化计数。
    // 隐私红线(与 utm_visits 同口径): 只记维度, 不采 IP/UA/referrer。
    db.exec(`
      CREATE TABLE IF NOT EXISTS feedback_events (
        date   TEXT    NOT NULL,  -- UTC+8 当日 YYYY-MM-DD
        page   TEXT    NOT NULL,  -- home | opc | blog | leaderboard
        action TEXT    NOT NULL,  -- open | submit
        count  INTEGER NOT NULL DEFAULT 1,
        PRIMARY KEY (date, page, action)
      );
    `);
  }
  db.exec(`PRAGMA user_version = ${SCHEMA_VERSION}`);
}

/** 默认库路径: server/data/knock.db(随 server/data gitignored, 不入库) */
function defaultDbPath() {
  return process.env.KNOCK_DB || path.join(__dirname, "..", "data", "knock.db");
}

module.exports = { openKnockDb, defaultDbPath, SCHEMA_VERSION };
