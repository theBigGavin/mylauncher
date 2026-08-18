# MyLauncher 发布红线（Release Policy）

**来源：Gavin 指令 2026-08-18③（原话：「mylauncher 的 release 包的发布我自己会做，你们不要自己编译发布 release 包」）**

## 规则（全团队强制）

mylauncher 的 release 包发布**一律由 Gavin 本人执行**：

- ❌ AI/agent **不** 执行 `./gradlew :app:assembleRelease`
- ❌ AI/agent **不** 打 tag（`git tag vX.Y.Z && git push origin vX.Y.Z`）
- ❌ AI/agent **不** 创建 GitHub Release（`gh release create`）

AI/agent 的职责边界：

- ✅ 只改代码 + 同一次提交里升版本号（CLAUDE.md 版本制度铁律：漏升视为 bug）
- ✅ `git commit && git push origin main`
- ✅ 交付说明里写清版本号即可，由 Gavin 编译签名并发布

原因：release 签名密钥库（`~/.android/mylauncher-keys/mylauncher-release.jks`）只在 Gavin 的机器上；
AI 侧本机无密钥库，assembleRelease 会产出 debug 签名包，无法覆盖安装正式签名的旧版本（会丢 DataStore 数据）。

## 生效范围

- 本仓库所有未来开发卡（含 bug 修复、功能迭代）
- 历史卡中要求「发版/gh release」的验收项一律按本红线作废对应部分
- 如 Gavin 后续改变此红线，以 Gavin 最新指令为准并更新本文档

## 遗留说明

- tag `v1.5.4`（指向 d3b2d36，2026-08-18 旧流程产物）：保留不动，后续 AI 不再打 tag
- 木鱼双响修复（三层防重入）已合入 main，Gavin 已迭代至 v1.6.7/code 38
