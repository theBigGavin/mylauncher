# 手速排行榜实施计划

## 目标

木鱼彩蛋的"最快手速"战绩上传到全球排行榜,作为病毒式传播钩子。娱乐榜(客户端上报即信任,轻校验),不做真防伪。

## 数据模型

| 字段 | 类型 | 说明 |
|---|---|---|
| nickname | string | 用户昵称,≤16 字符,去空白;空则服务端生成"木鱼玩家#XXXX" |
| rate | float | 次/秒 = 1000 / 最快连击间隔(ms),保留 1 位小数 |
| gapMs | int | 最快连击间隔(原始值,与 rate 互验) |
| samples | int | 参与纪录的连击样本数(用于轻校验) |
| region | string | 可选,客户端上报的国家/地区码(留空=不采集) |
| createdAt | int | 服务端时间戳(ms) |

服务端只存这些字段,不存设备标识/IP 映射。

## API 契约

Base: `https://<域名>/api/v1/knock`

### `GET /leaderboard?limit=10`

```json
{
  "leaderboard": [
    {"rank": 1, "nickname": "xxx", "rate": 23.3, "createdAt": 1755500000000}
  ],
  "totalPlayers": 1234,
  "myRank": null
}
```

- 取 rate 最高的 top-N(并列按 createdAt 升序,先到先排)。
- `myRank` 仅在携带 `?nickname=` 查询时返回该昵称最好成绩的名次(第几名,同分取最早),无成绩返回 null。

### `POST /submit`

请求: `{"nickname": "xxx", "rate": 23.3, "gapMs": 43, "samples": 82}`
响应: `{"rank": 12, "totalPlayers": 1234, "leaderboard": [...]}`(直接回 top10,省一次请求)

### 轻校验(服务端,拒绝即 400)

- `40 <= gapMs <= 2000`(等效 rate ≤ 25 次/秒;低于 40ms 视伪造/误报)
- `samples >= 10`(太少样本不构成"手速纪录")
- `abs(1000/rate - gapMs) <= 0.6`(rate 与 gapMs 互验)
- nickname 清洗后 ≤16 字符,否则拒绝
- 同 nickname 只保留最好成绩(更新式 upsert);限流:每 IP 每分钟 ≤5 次 submit

## 后端实现建议

Cloudflare Worker + KV(免费额度足够,全球边缘延迟低):

- KV 结构:`scores` = list(按 rate 排序的 top 条目,内存排序即可,玩家量级小)、`byNick` = nickname → 最好成绩(upsert 去重)。
- 单 Worker 文件即可,无数据库。若已有 hermes.cc.cd 服务器,同契约用任意栈实现(Node/Go + SQLite 单表即可)。

## App 端改动(客户端)

现有基础: `HomeStore.fastestKnockGapMs` 已持久化最快纪录;设置页彩蛋分组已有"最快手速"展示。

1. **设置页彩蛋分组新增"手速排行榜"小节**:
   - 昵称输入框(本地持久化,HomeStore 新键 `leaderboard_nickname`,默认空 → 显示服务端生成的默认名提示)
   - "上传我的手速"按钮:读 `fastestKnockGapMs`,满足 `gapMs >= 40 && samples >= 10` 才可传(样本数:KnockSound 需新增会话连击计数,随纪录一并上报;不足 10 时按钮置灰并提示"连击 10 次以上才能上传")
   - 榜单列表:top10(名次/昵称/次每秒),自己成绩高亮 + "我的名次"行
2. **手速分享图(纯客户端,不依赖后端,可先行上线)**:
   - 入口:排行榜小节下方"生成分享图"按钮
   - 内容:MyLauncher 品牌 + 最快手速大数字(如 23.3 次/秒)+ 昵称 + 每日最高功德 + **下载二维码**(扫码直达下载页)+ 文案"快来下载 MyLauncher 挑战我"
   - 生成:Canvas 自绘(复用壁纸色板保持视觉一致)→ Bitmap → 保存 MediaStore Pictures(Android 10+ 免权限),保存成功 Toast 路径;另提供系统分享(ACTION_SEND 带 PNG)
   - 二维码:ZXing core(仅 ~600KB,项目首个第三方运行时依赖,建议隔离在 `ui/share/` 下)
   - 下载地址抽常量 `DOWNLOAD_URL`,默认 GitHub Releases 最新页,可换官网落地页
3. **网络**:OkHttp 或 HttpURLConnection(项目无网络依赖,建议直接 HttpURLConnection + kotlinx.coroutines Dispatchers.IO,不加依赖);请求/响应按上面 JSON 契约。超时 10s,失败静默降级(榜单显示"加载失败,点击重试")。
4. **权限/隐私**:仅上报昵称与成绩,无任何设备标识;隐私说明一句放在上传按钮下方("仅上传昵称与手速成绩")。
5. **文案与文案开关**:排行榜小节在彩蛋分组内,未解锁彩蛋不显示;榜单域名抽成常量 `LEADERBOARD_BASE_URL`,便于换环境。

## 里程碑

1. 后端 Worker + 接口自测(curl 跑通三接口 + 校验用例)——后端同事
2. App 端 A 批(先行上线,不等后端):**手速分享图**(Canvas 生成/二维码/保存/分享)——客户端
3. App 端 B 批:昵称/上传/榜单 UI + 网络层 + 持久化——客户端
4. 联调(真机上传、榜单刷新、限流/校验边界)
5. 发版:版本按现有制度(新功能=次+1),README 中英文同步

## 多语言

- 本功能全部 UI 文案走 Android 资源包:`values/strings.xml`(中文默认)+ `values-en/strings.xml`(英文),**不硬编码在组件里**——现有代码全是内联中文,新功能从第一天就按规范来。
- 分享图文案:"快来下载 MyLauncher 挑战我" / "Download MyLauncher and beat my speed!";昵称/成绩/榜单标签同理。
- 分享图生成时按当前 Locale 取文案,生成的 PNG 随用户语言走。
- 后续(另行立项):存量页面的硬编码文案迁移到资源包、加 values-xx 更多语言。

## 风险与明确不做

- 伪造:纯客户端可无限伪造,娱乐定位,不做真防伪;后续若要严肃可加"连击间隔序列"服务端分布校验(仍有伪造空间)。
- 不做账号体系、不做社交(评论/关注)、不做地区榜(语言与地区榜无关,多语言照做)。
