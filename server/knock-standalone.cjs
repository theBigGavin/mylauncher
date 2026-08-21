// mylauncher · 手速排行榜独立进程入口（0819-i 迁出 mrd; 0822 P0-3a 迁入本仓 server/）
// 背景: 全球排行榜不应挂在 mrd 域名(mrd.hermes.cc.cd/api/v1/knock), 庄子拍板迁独立进程,
//       公网入口 https://hermes.cc.cd/api/v1/knock (cloudflared ingress → localhost:3032)。
// 职责: 仅监听 :3032, 只分发 /api/v1/knock/{leaderboard,percentile,submit} 三路由(复用
//       createKnockRoutes(db)), 与 index.cjs 契约一致: handler 返回 {__rawResponse: payload}
//       时原样输出裸 JSON, 错误回显 e.status + 白名单文案。
// CORS: Access-Control-Allow-Origin: * —— 公开娱乐榜无凭据; www.hermes.cc.cd 与 hermes.cc.cd
//       跨域调用必须放行; OPTIONS 预检返回 204 + CORS 头(POST 预检是 submit 正常路径, 务必处理)。
// 数据: SQLite 仍在 server/data/knock.db(默认路径, KNOCK_DB env 可覆盖), 与迁移前同一文件,
//       数据无缝迁移; mrd(:3000) 已移除 initKnock 加载, 避免双进程写同一库。
// 红线: 不碰 knock.db 数据文件内容; 非 knock 路径一律 404。
//       根路径 / 例外: 302 跳转官网 www.hermes.cc.cd (Gavin 反馈拍板 2026-08-19),
//       其余非 knock 路径仍 404。
"use strict";

const http = require("node:http");
const { openKnockDb, defaultDbPath } = require("./knock/db.cjs");
const { createKnockRoutes } = require("./knock/routes.cjs");

const PORT = Number(process.env.PORT || process.env.KNOCK_PORT || 3032);
const BODY_LIMIT = 256 * 1024; // 与 index.cjs 一致
const PARAM_MAX = 2000;        // 用户输入参数长度上限(与 index.cjs 一致)
const RATE_WINDOW_MS = 60 * 1000;
const RATE_MAX = 2400;         // 与 index.cjs 的 apiLimiter 一致: 每 IP 每分钟 2400 次

/* ---------------- 通用设施(与 index.cjs 行为对齐) ---------------- */

// 客户端 IP(限流 key): 环回对端(cloudflared 本机隧道)时采信 CF-Connecting-IP,
// 复用 knock/routes.cjs 的 clientIpOf(与托管层同思路, 防绕过隧道直连刷穿限流)。
const { clientIpOf } = require("./knock/routes.cjs");

// 滑动窗口限流器(与 index.cjs makeLimiter 同实现): 记录窗口内请求时间戳, 超 max 拒绝
function makeLimiter(windowMs, max) {
  const hits = new Map(); // ip -> number[] (请求时间戳)
  const sweeper = setInterval(() => {
    const cutoff = Date.now() - windowMs;
    for (const [ip, ts] of hits) {
      const idx = ts.findIndex((t) => t >= cutoff);
      if (idx > 0) hits.set(ip, ts.slice(idx));
      else if (idx === -1) hits.delete(ip);
    }
  }, Math.min(windowMs, 30000));
  sweeper.unref();
  return (ip) => {
    const now = Date.now();
    const cutoff = now - windowMs;
    let ts = hits.get(ip);
    if (!ts) { hits.set(ip, [now]); return true; }
    const idx = ts.findIndex((t) => t >= cutoff);
    if (idx > 0) ts = ts.slice(idx);
    else if (idx === -1) { hits.set(ip, [now]); return true; }
    ts.push(now);
    hits.set(ip, ts);
    return ts.length <= max;
  };
}

const apiLimiter = makeLimiter(RATE_WINDOW_MS, RATE_MAX);

/** 统一 JSON 响应(与 index.cjs send 同形状); CORS * 恒定下发 */
function send(res, code, payload, extra = {}) {
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Access-Control-Allow-Origin": "*",
    ...extra,
  };
  for (const k of Object.keys(headers)) if (headers[k] == null) delete headers[k];
  res.writeHead(code, headers);
  res.end(typeof payload === "string" ? payload : JSON.stringify(payload));
}

/** 预检响应: 204 + CORS 头(POST 预检是 submit 正常路径, 必须 2xx 放行) */
function sendPreflight(res) {
  res.writeHead(204, {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "600",
    "Cache-Control": "no-store",
  });
  res.end();
}

/** 读取请求体(限长), 返回 Buffer; tooBig 标记超限 */
function readBodyWithLimit(req, limit) {
  return new Promise((done) => {
    const chunks = [];
    let size = 0;
    let tooBig = false;
    req.on("data", (c) => {
      size += c.length;
      if (size > limit) { tooBig = true; req.destroy(); return; }
      chunks.push(c);
    });
    req.on("end", () => done({ buf: Buffer.concat(chunks), tooBig }));
    req.on("error", () => done({ buf: Buffer.concat(chunks), tooBig }));
    req.on("close", () => done({ buf: Buffer.concat(chunks), tooBig }));
  });
}

/* ---------------- 初始化: DB + 路由表(复用原模块, 行为逐字节一致) ---------------- */

const db = openKnockDb(defaultDbPath());
const routes = createKnockRoutes(db);

const server = http.createServer(async (req, res) => {
  try {
    const u = new URL(req.url, "http://localhost");

    // 根路径: 302 跳转公司官网 (Gavin 反馈 2026-08-19, 庄子拍板方案1; 目标写死防 open redirect)
    if (u.pathname === "/") {
      res.writeHead(302, { Location: "https://www.hermes.cc.cd/" });
      res.end();
      return;
    }

    // [0820-g] /go/* 短链: bare domain (hermes.cc.cd) 保留路径 302 到 www —— 短链体系以 www 为准,
    // bare 直接 404 会丢路径; 302 后由 www 侧 _worker.js 统一小写匹配 + 302 + 点击计数。
    // 目标拼接写死前缀防 open redirect, pathname 原样保留(大小写交给 www worker 归一)。
    if (u.pathname === "/go" || u.pathname === "/go/" || u.pathname.indexOf("/go/") === 0) {
      res.writeHead(302, { Location: "https://www.hermes.cc.cd" + u.pathname, "Cache-Control": "no-store" });
      res.end();
      return;
    }

    // 仅分发 /api/v1/knock/* 三路由; 其余路径一律 404
    if (!routes[u.pathname]) {
      send(res, 404, { ok: false, error: "not found" });
      return;
    }

    // OPTIONS 预检: 2xx + CORS 头(浏览器跨域 POST 前必发; 必须放行, 否则 submit 被拦)
    if (req.method === "OPTIONS") {
      sendPreflight(res);
      return;
    }

    // 按 IP 限流(先于业务校验, 防恶意突发; 与 index.cjs 同阈值)
    const ip = clientIpOf(req);
    if (!apiLimiter(ip)) {
      send(res, 429, { ok: false, error: "too many requests" });
      return;
    }

    // 参数长度上限(防无界缓存/内存)
    for (const v of u.searchParams.values()) {
      if (v.length > PARAM_MAX) {
        send(res, 400, { ok: false, error: "param too long" });
        return;
      }
    }

    // 解析 POST body(JSON); 空 body(如 GET 语义的 feedback 上报走 POST 无体) → undefined, 不报错。
    // submit 带体照常解析; 非 POST 无 body, 与 index.cjs 语义一致。
    let body;
    if (req.method === "POST") {
      const r = await readBodyWithLimit(req, BODY_LIMIT);
      if (r.tooBig) {
        send(res, 413, { ok: false, error: "payload too large" });
        return;
      }
      const raw = r.buf.toString().trim();
      if (!raw) {
        body = undefined;
      } else {
        try { body = JSON.parse(raw); } catch {
          send(res, 400, { ok: false, error: "invalid json body" });
          return;
        }
      }
    }

    const data = await routes[u.pathname](u.searchParams, body, req);
    // __rawResponse 约定: 契约要求裸 JSON 响应体(如 {"leaderboard":...}), 原样输出不套 ok/data 包装
    if (data && data.__rawResponse !== undefined) {
      send(res, 200, data.__rawResponse);
    } else {
      send(res, 200, { ok: true, data, ts: Date.now() });
    }
  } catch (e) {
    // 错误回显契约(与 index.cjs 一致): 内部细节只记日志; e.status 由业务错误携带,
    // message 必须为白名单文案; 无 status 一律回显静态 "upstream error"
    console.error("[knock-standalone]", e?.stack || e?.message || e);
    send(res, e?.status || 502, { ok: false, error: e?.status ? e.message : "upstream error" });
  }
});

server.listen(PORT, () => {
  console.log(`[knock] 手速排行榜独立进程已启动 — :${PORT}/api/v1/knock/{leaderboard,percentile,submit,track,feedback} (SQLite)`);
});
