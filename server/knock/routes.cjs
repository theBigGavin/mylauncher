// mrd · 手速排行榜路由（挂载到 index.cjs 的 routes 对象，只增不改核心路由）
// 契约(Base: https://mrd.hermes.cc.cd/api/v1/knock):
//   GET  /leaderboard?limit=10[&nickname=] → {leaderboard:[{rank,nickname,rate,createdAt}],totalPlayers,myRank}
//   GET  /percentile?rate=23.3             → {percentile:87.5}   (超过的全球用户比例 0-100, 无写入)
//   POST /submit {nickname,rate,gapMs,samples[,region]} → {rank,totalPlayers,leaderboard}
// 轻校验(拒绝即 400): gapMs>0 && gapMs<=2000; samples>=10; abs(1000/rate-gapMs)<=0.6;
//   nickname 清洗后 ≤16 字符; 同 nickname 只保留最好成绩(upsert); 限流每 IP 每分钟 ≤5 次 submit。
// 响应为契约裸 JSON(handler 返回 {__rawResponse: <payload>} 由 index.cjs 原样输出, 不套 ok/data 包装)。
"use strict";
const crypto = require("crypto");

const NICKNAME_MAX = 16;
const GAP_MIN = 0; // 下限完全放开: gapMs 须 >0(0819-p Gavin 指令, 对应速率无上限; 上限 GAP_MAX 保留防无意义慢速)
const GAP_MAX = 2000;
const SAMPLES_MIN = 10;
const RATE_TOLERANCE = 0.6; // abs(1000/rate - gapMs) 容差(rate 保留 1 位小数)
const LB_DEFAULT_LIMIT = 10;
const LB_MAX_LIMIT = 100;
const REGION_MAX = 16; // 国家/地区码最长(如 "zh-CN")
const SUBMIT_WINDOW_MS = 60 * 1000;
const SUBMIT_MAX_PER_WINDOW = 5;

// 反馈埋点白名单(0820-fb): 页面与动作各白名单校验, 非法一律忽略不计数
const FEEDBACK_PAGES = new Set(["home", "opc", "blog", "leaderboard"]);
const FEEDBACK_ACTIONS = new Set(["open", "submit"]);

/** 携带 HTTP 状态码的业务错误(index.cjs 错误回显契约: e.status + 白名单文案) */
function httpError(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

/**
 * 客户端 IP(限流 key): 环回对端(cloudflared 本机隧道)时采信 CF-Connecting-IP,
 * 否则用 socket 地址 — 防止绕过隧道直连时伪造代理头刷穿限流。与 hosting/routes.cjs 同思路。
 */
function clientIpOf(req) {
  const peer = req?.socket?.remoteAddress || "unknown";
  const loopback = peer === "127.0.0.1" || peer === "::1" || peer === "::ffff:127.0.0.1";
  if (loopback) {
    const cf = req?.headers?.["cf-connecting-ip"];
    if (typeof cf === "string" && cf.trim()) return cf.trim();
    const xff = req?.headers?.["x-forwarded-for"];
    if (typeof xff === "string" && xff.trim()) return xff.split(",")[0].trim();
  }
  return peer;
}

/** 滑动窗口限流器(每 IP 每窗口 ≤max 次; 内存计数不落库, 不存 IP 映射) */
function makeSubmitLimiter(windowMs, max) {
  const hits = new Map(); // ip -> number[] (提交时间戳)
  const sweeper = setInterval(() => {
    const cutoff = Date.now() - windowMs;
    for (const [ip, ts] of hits) {
      while (ts.length && ts[0] < cutoff) ts.shift();
      if (ts.length === 0) hits.delete(ip);
    }
  }, Math.min(windowMs, 30000));
  sweeper.unref();
  return {
    /** 记录一次提交; 窗口内已达 max 返回 false(拒绝), 否则 true */
    hit(ip) {
      const now = Date.now();
      const cutoff = now - windowMs;
      const arr = (hits.get(ip) || []).filter((t) => t >= cutoff);
      if (arr.length >= max) {
        hits.set(ip, arr);
        return false;
      }
      arr.push(now);
      hits.set(ip, arr);
      return true;
    },
  };
}

/** 昵称清洗: 去除全部空白(trim + 内部空白压平); 返回清洗后字符串 */
function cleanNickname(raw) {
  return String(raw ?? "").replace(/\s+/g, "");
}

/** 服务端生成匿名昵称「木鱼玩家#XXXX」: 随机 4 位数字, 避开已存在昵称(撞名重试, 兜底用时间戳) */
function genAnonymousNickname(db) {
  for (let i = 0; i < 10; i++) {
    const n = "木鱼玩家#" + String(crypto.randomInt(10000)).padStart(4, "0");
    const exists = db.prepare("SELECT 1 FROM knock_scores WHERE nickname = ?").get(n);
    if (!exists) return n;
  }
  return "木鱼玩家#" + String(Date.now() % 10000).padStart(4, "0");
}

/** top-N 榜单(并列 rate 按 createdAt 升序, 先到先排); rank 1-based */
function topN(db, limit) {
  const rows = db
    .prepare("SELECT nickname, rate, created_at FROM knock_scores ORDER BY rate DESC, created_at ASC LIMIT ?")
    .all(limit);
  return rows.map((r, i) => ({ rank: i + 1, nickname: r.nickname, rate: r.rate, createdAt: r.created_at }));
}

/** 某昵称当前名次(1-based; 无成绩返回 null) — 同分取最早 */
function rankOf(db, nickname) {
  const me = db.prepare("SELECT rate, created_at FROM knock_scores WHERE nickname = ?").get(nickname);
  if (!me) return null;
  const { n } = db
    .prepare(
      "SELECT COUNT(*) AS n FROM knock_scores WHERE rate > ? OR (rate = ? AND created_at < ?)"
    )
    .get(me.rate, me.rate, me.created_at);
  return n + 1;
}

/** 该成绩超过的全球用户比例(0-100, 1 位小数); 低于自己 rate 的用户占比 */
function percentileOf(db, rate) {
  const { total } = db.prepare("SELECT COUNT(*) AS total FROM knock_scores").get();
  if (!total) return 0;
  const { below } = db.prepare("SELECT COUNT(*) AS below FROM knock_scores WHERE rate < ?").get(rate);
  return Math.round((below / total) * 1000) / 10;
}

/** 条件 upsert: 同 nickname 只在 rate 更高时更新(保留最好成绩); 返回是否更新 */
function upsertScore(db, { nickname, rate, gapMs, samples, region }) {
  const res = db
    .prepare(
      `INSERT INTO knock_scores (nickname, rate, gap_ms, samples, region, created_at)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(nickname) DO UPDATE SET
        rate = excluded.rate, gap_ms = excluded.gap_ms, samples = excluded.samples,
        region = excluded.region, created_at = excluded.created_at
       WHERE excluded.rate > knock_scores.rate`
    )
    .run(nickname, rate, gapMs, samples, region || null, Date.now());
  return res.changes > 0;
}

/** UTC+8 当日 YYYY-MM-DD(榜单页以中国时区为日界; 只做聚合键, 不存时间戳) */
function utc8DateStr(now = Date.now()) {
  const d = new Date(now + 8 * 3600 * 1000);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
}

/** UTM 参数清洗: 去首尾空白; 空/null → null(不计数); 超长截断(防刷爆 DB 行宽) */
function cleanUtm(raw, maxLen = 128) {
  if (raw == null) return null;
  const s = String(raw).trim();
  if (!s) return null;
  return s.length > maxLen ? s.slice(0, maxLen) : s;
}

/**
 * 创建排行榜路由表。db: openKnockDb 打开的实例。
 * handler 签名与 index.cjs routes 一致: (searchParams, body, req)。
 */
function createKnockRoutes(db) {
  const submitLimiter = makeSubmitLimiter(SUBMIT_WINDOW_MS, SUBMIT_MAX_PER_WINDOW);

  return {
    "/api/v1/knock/leaderboard": async (q) => {
      let limit = parseInt(q.get("limit") || "", 10);
      if (!Number.isInteger(limit) || limit <= 0) limit = LB_DEFAULT_LIMIT;
      if (limit > LB_MAX_LIMIT) limit = LB_MAX_LIMIT;
      const nick = cleanNickname(q.get("nickname") || "");
      const payload = {
        leaderboard: topN(db, limit),
        totalPlayers: db.prepare("SELECT COUNT(*) AS n FROM knock_scores").get().n,
        myRank: null,
      };
      if (nick) payload.myRank = rankOf(db, nick);
      return { __rawResponse: payload };
    },

    "/api/v1/knock/percentile": async (q) => {
      const rate = parseFloat(q.get("rate") || "");
      if (!Number.isFinite(rate) || rate <= 0) throw httpError(400, "invalid rate");
      return { __rawResponse: { percentile: percentileOf(db, rate) } };
    },

    "/api/v1/knock/submit": async (_q, body, req) => {
      const b = body || {};
      // 限流(先于业务校验, 防刷校验接口; 每 IP 每分钟 ≤5 次, 第 6 次拒)
      const ip = clientIpOf(req);
      if (!submitLimiter.hit(ip)) throw httpError(400, "too many submits");

      // nickname: 清洗(去空白) → ≤16 字符; 空则服务端生成
      const nickname = cleanNickname(b.nickname);
      if (nickname.length > NICKNAME_MAX) throw httpError(400, "nickname too long");

      // rate: 有限正数
      const rate = b.rate;
      if (typeof rate !== "number" || !Number.isFinite(rate) || rate <= 0) {
        throw httpError(400, "invalid rate");
      }
      // gapMs: 整数, 0 < gapMs <= 2000(下限完全放开, 上限保留防无意义慢速)
      const gapMs = b.gapMs;
      if (!Number.isInteger(gapMs) || gapMs <= GAP_MIN || gapMs > GAP_MAX) {
        throw httpError(400, "invalid gapMs");
      }
      // samples: 整数, >= 10
      const samples = b.samples;
      if (!Number.isInteger(samples) || samples < SAMPLES_MIN) {
        throw httpError(400, "invalid samples");
      }
      // rate 与 gapMs 互验: rate = 1000/gapMs 保留 1 位小数
      if (Math.abs(1000 / rate - gapMs) > RATE_TOLERANCE) {
        throw httpError(400, "rate mismatch");
      }
      // region: 可选, 清洗后 ≤16 字符; 空串 → 不采集(NULL)
      let region = null;
      if (b.region != null) {
        region = cleanNickname(b.region);
        if (region.length > REGION_MAX) throw httpError(400, "invalid region");
        if (!region) region = null;
      }

      const finalNick = nickname || genAnonymousNickname(db);
      const finalRate = Math.round((1000 / gapMs) * 10) / 10; // 服务端按 gapMs 重算, 保留 1 位小数
      upsertScore(db, { nickname: finalNick, rate: finalRate, gapMs, samples, region });

      return {
        __rawResponse: {
          rank: rankOf(db, finalNick),
          totalPlayers: db.prepare("SELECT COUNT(*) AS n FROM knock_scores").get().n,
          leaderboard: topN(db, LB_DEFAULT_LIMIT),
        },
      };
    },

    // UTM 引流跟踪(0819-x Gavin 指令): 榜单页埋点 fire-and-forget 上报。
    // 隐私红线: 只记渠道维度(utm_*), 不记 IP / UA / referrer, 与榜单页 footer 隐私声明一致。
    // 语义: 三参均可选, 全空则忽略不计数; 命中 (date,source,medium,campaign) upsert count+1;
    //       date = UTC+8 当日 YYYY-MM-DD。复用分发层 apiLimiter + CORS(*), 响应固定 {ok:true}。
    "/api/v1/knock/track": async (q) => {
      const source = cleanUtm(q.get("utm_source"));
      const medium = cleanUtm(q.get("utm_medium"));
      const campaign = cleanUtm(q.get("utm_campaign"));
      if (source == null && medium == null && campaign == null) {
        // 全空: 忽略不计数(如直接访问无参)
        return { __rawResponse: { ok: true } };
      }
      db.prepare(
        `INSERT INTO utm_visits (date, source, medium, campaign, count)
         VALUES (?, ?, ?, ?, 1)
         ON CONFLICT(date, source, medium, campaign) DO UPDATE SET count = count + 1`
      ).run(utc8DateStr(), source ?? "", medium ?? "", campaign ?? "");
      return { __rawResponse: { ok: true } };
    },

    // 全站悬浮反馈按钮转化统计(0820-fb Gavin 指令): 各页反馈 open/submit 计数埋点。
    // 提交内容复用 /api/assistant(不落这里), 本端点只做转化计数。
    // 隐私红线(与 utm_visits 同口径): 只记维度(page/action), 不记 IP/UA/referrer。
    // 语义: page/action 各白名单校验, 缺参或非法 → {ok:true} 忽略不计数(fire-and-forget);
    //       命中 (date,page,action) upsert count+1, date = UTC+8 当日 YYYY-MM-DD。
    //       复用分发层 apiLimiter + CORS(*), 响应固定 {ok:true}。
    "/api/v1/knock/feedback": async (q) => {
      const page = String(q.get("page") || "").trim();
      const action = String(q.get("action") || "").trim();
      if (!FEEDBACK_PAGES.has(page) || !FEEDBACK_ACTIONS.has(action)) {
        // 缺参/非法维度: 静默忽略不计数
        return { __rawResponse: { ok: true } };
      }
      db.prepare(
        `INSERT INTO feedback_events (date, page, action, count)
         VALUES (?, ?, ?, 1)
         ON CONFLICT(date, page, action) DO UPDATE SET count = count + 1`
      ).run(utc8DateStr(), page, action);
      return { __rawResponse: { ok: true } };
    },
  };
}

module.exports = { createKnockRoutes, cleanNickname, clientIpOf, NICKNAME_MAX };
