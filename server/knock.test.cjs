// mrd · 手速排行榜单测（node --test）
// 覆盖: 三接口契约(裸 JSON) / gapMs=0 拒 / gapMs=24(41.7/s) 接受 / samples=9 拒 / rate 与 gapMs 互验不匹配拒 /
//       昵称超长拒 / 空昵称服务端生成 / 同昵称 upsert 只留最好成绩 / 限流每 IP 每分钟 5 次第 6 次拒 /
//       percentile 比例计算。
"use strict";
const test = require("node:test");
const assert = require("node:assert");

const { openKnockDb } = require("./knock/db.cjs");
const { createKnockRoutes } = require("./knock/routes.cjs");

/** 内存库: 每次测试独立, 无文件残留 */
function fresh() {
  const db = openKnockDb(":memory:");
  return { db, routes: createKnockRoutes(db) };
}

/** 假 req: remoteAddress 可注入(限流测试用不同 IP 或同 IP) */
function fakeReq(ip = "1.2.3.4") {
  return { method: "POST", socket: { remoteAddress: ip }, headers: {} };
}

/** 调用路由 handler: 返回 {status, payload}; 业务错误捕获为 {status, error} */
async function call(routes, path, { q, body, req } = {}) {
  const handler = routes[path];
  assert.ok(handler, `route exists: ${path}`);
  const searchParams = new URLSearchParams(q || {});
  try {
    const data = await handler(searchParams, body, req || fakeReq());
    return { status: 200, payload: data.__rawResponse !== undefined ? data.__rawResponse : data };
  } catch (e) {
    return { status: e.status || 502, error: e.message };
  }
}

const LEADERBOARD = "/api/v1/knock/leaderboard";
const PERCENTILE = "/api/v1/knock/percentile";
const SUBMIT = "/api/v1/knock/submit";
const TRACK = "/api/v1/knock/track";
const FEEDBACK = "/api/v1/knock/feedback";

/** 提交一条合法成绩(IP 可变, 便于并发写入测试) */
async function submit(routes, overrides = {}, ip = "1.2.3.4") {
  const body = Object.assign({ nickname: "tester", rate: 23.3, gapMs: 43, samples: 82 }, overrides);
  return call(routes, SUBMIT, { body, req: fakeReq(ip) });
}

test("契约: 空库 leaderboard 返回 {leaderboard:[], totalPlayers:0, myRank:null}", async () => {
  const { routes } = fresh();
  const r = await call(routes, LEADERBOARD);
  assert.strictEqual(r.status, 200);
  assert.deepStrictEqual(r.payload, { leaderboard: [], totalPlayers: 0, myRank: null });
});

test("submit 合法成绩 → 裸响应 {rank,totalPlayers,leaderboard}, top1 含 rank/nickname/rate/createdAt", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { nickname: "快手" });
  assert.strictEqual(r.status, 200);
  assert.strictEqual(r.payload.rank, 1);
  assert.strictEqual(r.payload.totalPlayers, 1);
  assert.strictEqual(r.payload.leaderboard.length, 1);
  const top = r.payload.leaderboard[0];
  assert.deepStrictEqual(
    Object.keys(top).sort(),
    ["createdAt", "nickname", "rank", "rate"].sort(),
    "榜单条目只含契约四字段"
  );
  assert.strictEqual(top.nickname, "快手");
  assert.strictEqual(top.rank, 1);
  assert.strictEqual(top.rate, 23.3, "rate 保留 1 位小数");
  assert.ok(Number.isInteger(top.createdAt));
});

test("轻校验: gapMs=0(≤0) 拒绝 400; gapMs=-1(负数) 拒绝 400", async () => {
  const { routes } = fresh();
  const r0 = await submit(routes, { gapMs: 0, rate: 1000 });
  assert.strictEqual(r0.status, 400, "gapMs=0 拒绝");
  const rn = await submit(routes, { gapMs: -1, rate: 1000 });
  assert.strictEqual(rn.status, 400, "gapMs=-1 拒绝");
});

test("轻校验: gapMs=24/rate=41.7(Gavin 41.7/s 场景, 原 <40 被拒) → 接受", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { nickname: "快手24", gapMs: 24, rate: 41.7, samples: 10 });
  assert.strictEqual(r.status, 200);
  assert.strictEqual(r.payload.rank, 1);
  assert.strictEqual(r.payload.leaderboard[0].rate, 41.7, "rate 保留 1 位小数");
});

test("轻校验: samples=9(<10) 拒绝 400", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { samples: 9 });
  assert.strictEqual(r.status, 400);
});

test("轻校验: rate 与 gapMs 互验不匹配(gapMs=43 而 rate=50) 拒绝 400", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { gapMs: 43, rate: 50 });
  assert.strictEqual(r.status, 400);
});

test("轻校验: gapMs=2000(上限) 与 rate 互验匹配 → 接受", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { nickname: "慢手", gapMs: 2000, rate: 0.5, samples: 10 });
  assert.strictEqual(r.status, 200);
  assert.strictEqual(r.payload.rank, 1);
});

test("轻校验: nickname 清洗后超 16 字符 拒绝 400; 内部空白被去除", async () => {
  const { routes } = fresh();
  // 18 个字符(含内部空白) → 清洗后 15 字符 → 接受(去空白生效)
  const r1 = await submit(routes, { nickname: "abc def ghi jkl mno" });
  assert.strictEqual(r1.status, 200);
  assert.strictEqual(r1.payload.leaderboard[0].nickname, "abcdefghijklmno");
  // 20 个字符 → 清洗后仍 20 → 拒绝
  const r2 = await submit(routes, { nickname: "一二三四五六七八九十一二三四五六七八九十一二" }, "1.2.3.5");
  assert.strictEqual(r2.status, 400);
});

test("匿名昵称: 空 nickname 由服务端生成「木鱼玩家#XXXX」", async () => {
  const { routes } = fresh();
  const r = await submit(routes, { nickname: "   " });
  assert.strictEqual(r.status, 200);
  assert.match(r.payload.leaderboard[0].nickname, /^木鱼玩家#\d{4}$/);
});

test("同 nickname upsert: 只保留最好成绩(先快后慢不覆盖, 先慢后快覆盖)", async () => {
  const { routes } = fresh();
  // 第一次: 慢成绩 rate=10 (gapMs=100)
  await submit(routes, { nickname: "小明", gapMs: 100, rate: 10 });
  // 第二次: 更慢 rate=5 (gapMs=200) → 不覆盖, 仍保留 10
  await submit(routes, { nickname: "小明", gapMs: 200, rate: 5 });
  let r = await call(routes, LEADERBOARD, { q: { nickname: "小明" } });
  assert.strictEqual(r.payload.totalPlayers, 1, "同昵称只算一个玩家");
  assert.strictEqual(r.payload.leaderboard[0].rate, 10, "更慢成绩不覆盖");
  // 第三次: 更快 rate=25 (gapMs=40) → 覆盖为 25
  await submit(routes, { nickname: "小明", gapMs: 40, rate: 25 });
  r = await call(routes, LEADERBOARD, { q: { nickname: "小明" } });
  assert.strictEqual(r.payload.leaderboard[0].rate, 25, "更快成绩覆盖");
  assert.strictEqual(r.payload.totalPlayers, 1);
  assert.strictEqual(r.payload.myRank, 1);
});

test("排名: 并列 rate 先到先排; 不同玩家各占一名次", async () => {
  const { routes } = fresh();
  // 先提交 rate=10(慢), 再提交 rate=25(快), 再同 rate=25(第二快先到者排前)
  await submit(routes, { nickname: "a", gapMs: 100, rate: 10 }, "1.2.3.1");
  await submit(routes, { nickname: "b", gapMs: 40, rate: 25 }, "1.2.3.2");
  await submit(routes, { nickname: "c", gapMs: 40, rate: 25 }, "1.2.3.3");
  const r = await call(routes, LEADERBOARD, { q: { nickname: "a" } });
  assert.deepStrictEqual(
    r.payload.leaderboard.map((x) => x.nickname),
    ["b", "c", "a"]
  );
  assert.strictEqual(r.payload.totalPlayers, 3);
  assert.strictEqual(r.payload.myRank, 3);
  const rb = await call(routes, LEADERBOARD, { q: { nickname: "b" } });
  assert.strictEqual(rb.payload.myRank, 1);
  // limit 生效
  const r1 = await call(routes, LEADERBOARD, { q: { limit: 1 } });
  assert.strictEqual(r1.payload.leaderboard.length, 1);
});

test("percentile: 超过比例正确(0-100, 1 位小数), 不产生写入", async () => {
  const { routes, db } = fresh();
  // 4 个玩家: rate 10, 20, 22.2, 25 (gapMs 全部合法 40~2000)
  await submit(routes, { nickname: "p1", gapMs: 100, rate: 10 }, "1.2.3.1");
  await submit(routes, { nickname: "p2", gapMs: 50, rate: 20 }, "1.2.3.2");
  await submit(routes, { nickname: "p3", gapMs: 45, rate: 22.2 }, "1.2.3.3");
  await submit(routes, { nickname: "p4", gapMs: 40, rate: 25 }, "1.2.3.4");
  const r = await call(routes, PERCENTILE, { q: { rate: "20" } });
  assert.strictEqual(r.status, 200);
  assert.strictEqual(r.payload.percentile, 25, "rate=20 超过 4 人中 1 人 = 25%");
  const rTop = await call(routes, PERCENTILE, { q: { rate: "25" } });
  assert.strictEqual(rTop.payload.percentile, 75, "rate=25 超过 3 人 = 75%");
  const rMid = await call(routes, PERCENTILE, { q: { rate: "22.2" } });
  assert.strictEqual(rMid.payload.percentile, 50);
  const rLow = await call(routes, PERCENTILE, { q: { rate: "10" } });
  assert.strictEqual(rLow.payload.percentile, 0);
  // 不产生写入
  const total = db.prepare("SELECT COUNT(*) AS n FROM knock_scores").get().n;
  assert.strictEqual(total, 4);
  // 非法 rate → 400
  const bad = await call(routes, PERCENTILE, { q: { rate: "abc" } });
  assert.strictEqual(bad.status, 400);
});

test("限流: 每 IP 每分钟 ≤5 次 submit, 第 6 次拒绝 400; 其他 IP 不受影响", async () => {
  const { routes } = fresh();
  let last;
  for (let i = 0; i < 5; i++) {
    last = await submit(routes, { nickname: "u" + i, gapMs: 40 + i, rate: 1000 / (40 + i) }, "9.9.9.9");
  }
  assert.strictEqual(last.status, 200, "前 5 次通过");
  const sixth = await submit(routes, { nickname: "u5", gapMs: 45, rate: 1000 / 45 }, "9.9.9.9");
  assert.strictEqual(sixth.status, 400, "第 6 次拒绝");
  // 其他 IP 正常
  const other = await submit(routes, { nickname: "u6", gapMs: 46, rate: 1000 / 46 }, "9.9.9.10");
  assert.strictEqual(other.status, 200);
});

test("track: 带 UTM 三参 → 固定响应 {ok:true} 且落库 count=1", async () => {
  const { routes, db } = fresh();
  const r = await call(routes, TRACK, { q: { utm_source: "mylauncher", utm_medium: "share_image", utm_campaign: "leaderboard" } });
  assert.strictEqual(r.status, 200);
  assert.deepStrictEqual(r.payload, { ok: true }, "响应固定 {ok:true}(裸 JSON 不套包装)");
  const rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 1);
  assert.strictEqual(rows[0].source, "mylauncher");
  assert.strictEqual(rows[0].medium, "share_image");
  assert.strictEqual(rows[0].campaign, "leaderboard");
  assert.strictEqual(rows[0].count, 1);
  assert.match(rows[0].date, /^\d{4}-\d{2}-\d{2}$/, "date = UTC+8 当日 YYYY-MM-DD");
  // 与当前 UTC+8 日期一致
  const now = Date.now() + 8 * 3600 * 1000;
  const expectDate = new Date(now).toISOString().slice(0, 10);
  assert.strictEqual(rows[0].date, expectDate);
});

test("track: 同渠道重复上报 → upsert count 递增(不新增行)", async () => {
  const { routes, db } = fresh();
  const q = { utm_source: "srcA", utm_medium: "medB", utm_campaign: "camC" };
  await call(routes, TRACK, { q });
  await call(routes, TRACK, { q });
  await call(routes, TRACK, { q });
  const rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 1, "同 (date,source,medium,campaign) 只一行");
  assert.strictEqual(rows[0].count, 3, "count 累加");
});

test("track: 部分参数缺失 → 落库空串补位; 全空 → 忽略不计数", async () => {
  const { routes, db } = fresh();
  // 仅 utm_source
  const r1 = await call(routes, TRACK, { q: { utm_source: "only_src" } });
  assert.strictEqual(r1.status, 200);
  assert.deepStrictEqual(r1.payload, { ok: true });
  let rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 1);
  assert.strictEqual(rows[0].source, "only_src");
  assert.strictEqual(rows[0].medium, "");
  assert.strictEqual(rows[0].campaign, "");
  // 全空(无参)
  const r2 = await call(routes, TRACK, { q: {} });
  assert.strictEqual(r2.status, 200);
  assert.deepStrictEqual(r2.payload, { ok: true });
  rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 1, "全空不落库");
  // 空白串参数视同全空
  const r3 = await call(routes, TRACK, { q: { utm_source: "  ", utm_medium: "", utm_campaign: "\t" } });
  assert.strictEqual(r3.status, 200);
  rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 1, "空白串视同空, 不计数");
  // 带前导/尾随空白 → 清洗后计数
  const r4 = await call(routes, TRACK, { q: { utm_source: "  mylauncher  " } });
  rows = db.prepare("SELECT * FROM utm_visits").all();
  assert.strictEqual(rows.length, 2);
  assert.strictEqual(rows[1].source, "mylauncher", "空白已 trim");
});

test("feedback: 同维度 3 次上报 → 固定 {ok:true} 且单行 count=3(upsert)", async () => {
  const { routes, db } = fresh();
  const q = { page: "home", action: "open" };
  for (let i = 0; i < 3; i++) {
    const r = await call(routes, FEEDBACK, { q });
    assert.strictEqual(r.status, 200);
    assert.deepStrictEqual(r.payload, { ok: true }, "响应固定 {ok:true}(裸 JSON 不套包装)");
  }
  const rows = db.prepare("SELECT * FROM feedback_events").all();
  assert.strictEqual(rows.length, 1, "同 (date,page,action) 只一行");
  assert.strictEqual(rows[0].page, "home");
  assert.strictEqual(rows[0].action, "open");
  assert.strictEqual(rows[0].count, 3, "count 累加");
  assert.match(rows[0].date, /^\d{4}-\d{2}-\d{2}$/, "date = UTC+8 当日 YYYY-MM-DD");
  const now = Date.now() + 8 * 3600 * 1000;
  assert.strictEqual(rows[0].date, new Date(now).toISOString().slice(0, 10));
});

test("feedback: 白名单校验 — page=evil / action=evil → {ok:true} 且不计数", async () => {
  const { routes, db } = fresh();
  const r1 = await call(routes, FEEDBACK, { q: { page: "evil", action: "open" } });
  assert.strictEqual(r1.status, 200);
  assert.deepStrictEqual(r1.payload, { ok: true });
  const r2 = await call(routes, FEEDBACK, { q: { page: "home", action: "evil" } });
  assert.strictEqual(r2.status, 200);
  assert.deepStrictEqual(r2.payload, { ok: true });
  const r3 = await call(routes, FEEDBACK, { q: { page: "home", action: "OPEN" } });
  assert.strictEqual(r3.status, 200, "大小写敏感: OPEN 非法");
  const n = db.prepare("SELECT COUNT(*) AS n FROM feedback_events").get().n;
  assert.strictEqual(n, 0, "非法维度全部忽略不计数");
});

test("feedback: 缺参 → {ok:true} 不计数(含空白串)", async () => {
  const { routes, db } = fresh();
  for (const q of [{}, { page: "home" }, { action: "open" }, { page: "  ", action: "" }]) {
    const r = await call(routes, FEEDBACK, { q });
    assert.strictEqual(r.status, 200);
    assert.deepStrictEqual(r.payload, { ok: true });
  }
  const n = db.prepare("SELECT COUNT(*) AS n FROM feedback_events").get().n;
  assert.strictEqual(n, 0, "缺参一律不计数");
});

test("feedback: 不同 page/action 分行; 不同日期分行", async () => {
  const { routes, db } = fresh();
  await call(routes, FEEDBACK, { q: { page: "home", action: "open" } });
  await call(routes, FEEDBACK, { q: { page: "home", action: "submit" } });
  await call(routes, FEEDBACK, { q: { page: "opc", action: "open" } });
  await call(routes, FEEDBACK, { q: { page: "leaderboard", action: "submit" } });
  await call(routes, FEEDBACK, { q: { page: "blog", action: "open" } });
  // 预插一条历史日期(昨天)的同维度行 → 不覆盖当天, 各自成行
  const yesterday = new Date(Date.now() + 8 * 3600 * 1000 - 24 * 3600 * 1000).toISOString().slice(0, 10);
  db.prepare("INSERT INTO feedback_events (date, page, action, count) VALUES (?, 'home', 'open', 1)")
    .run(yesterday);
  const rows = db.prepare("SELECT date, page, action, count FROM feedback_events ORDER BY page, action, date").all();
  assert.strictEqual(rows.length, 6, "不同 page/action + 不同日期 = 6 行");
  assert.deepStrictEqual(
    rows.map((r) => `${r.page}/${r.action}/${r.date}`),
    [
      "blog/open/" + new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10),
      "home/open/" + yesterday,
      "home/open/" + new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10),
      "home/submit/" + new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10),
      "leaderboard/submit/" + new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10),
      "opc/open/" + new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10),
    ]
  );
});
