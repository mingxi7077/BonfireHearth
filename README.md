# BonfireHearth

[English](#english) | [简体中文](#简体中文)

BonfireHearth is a Bonfire-owned Purpur fork for heavy-plugin Minecraft production servers.

BonfireHearth 是 Bonfire 自有的 Purpur 分支核心，面向重插件 Minecraft 生产服务器。

---

## English

BonfireHearth is a Bonfire-owned Purpur fork for heavy-plugin Minecraft production servers.

It keeps Paper/Purpur plugin compatibility as high as practical while adding Bonfire-specific runtime optimizations for entity pressure, scheduler pressure, startup spikes, low-memory observation, and business-server-oriented vanilla spawn shutdown.

### Project Position

- Codename: `炉心`
- Base upstream: `Purpur`
- Current target line: `1.21.8`
- Primary goal: keep high Paper/Purpur API compatibility while improving real-world throughput for Bonfire-style heavy-plugin worlds

### Why It Exists

The Bonfire production environment is not a vanilla survival server.
It is a large plugin-driven world with dense entities, model engines, scheduler hotspots, and packet pressure.

BonfireHearth exists to optimize that workload without turning the core into a low-compatibility experimental branch.

### Current Optimization Scope

- Entity tracking and sync budgeting
- Low-dynamic living-entity packet throttling
- Mob AI budgeting for low-priority idle mobs
- Sync scheduler budgeting and hotspot diagnostics
- Chunk send budgeting
- Chunk load and generation admission control
- Network-pressure diagnostics
- Startup guard and warmup smoothing
- Low-memory observation hooks
- Vanilla natural spawning shutdown for Bonfire business-mode servers

### What BonfireHearth Improves

BonfireHearth is not trying to win with a deep thread-model rewrite.
Its main value is compatibility-first load shaping for heavy-plugin production worlds:

- reduce packet churn from dense low-dynamic entities and model carriers
- smooth startup spikes instead of letting plugin sync work burst into the first live minute
- cap chunk send, chunk load, and generation pressure before they amplify lag
- reduce wasted vanilla spawning work for business-mode servers that do not rely on vanilla natural mobs
- keep the Purpur/Paper plugin surface as intact as practical

### Observed Local Validation Results

These numbers come from the Bonfire gray and RC validation path and should be read as project-side field data, not as a universal benchmark:

- In the Bonfire production baseline before this fork, an optimized Purpur stack with about `130` plugins and roughly `20` major plugins could only hold around `50` players before tick loss started becoming visible.
- In local MythicMobs / model-heavy scenes, earlier baseline behavior started becoming clearly unhealthy around `100` dense mobs; the Bonfire gray workline pushed the more serious degradation point to around `140`.
- In later local validation, scenes above `160` MM mobs could still remain meaningfully responsive on the same-machine test path, although much denser scenes still showed obvious network-pressure and memory-pressure growth.
- Under `-Xmx2G`, the point where memory pressure turned visibly unhealthy was also pushed back from roughly `100` dense mobs to roughly `140` in the validated gray line.

### How It Achieves That

BonfireHearth gets its gains mostly through targeted runtime control rather than one giant rewrite:

- packet-level throttling for low-dynamic visual sync instead of bluntly suppressing full entity updates
- entity-tracking, AI, scheduler, chunk-send, and chunk-load budgets that degrade pressure gradually
- startup guard logic that soft-defers non-critical sync work during the dangerous first live minute
- low-memory observation and business-mode fast paths to stop spending budget on unwanted vanilla systems
- diagnostics for network pressure, hot plugins, sync pressure, and release verification instead of tuning blind

### Current Release

- Recommended version: `1.21.8 RC1`
- Release package: `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`
- SHA256: `EB6D60C143A3BBDEBD750BA027B2A57EC67A214A367296E04B9C987934759645`
- Build line: `1.21.8-R0.1-BONFIRE-RC.1`
- Runtime smoke validation: local Bonfire furnace stack reached `Done (71.145s)!`

### Workspace Layout

- `docs/`: planning, roadmap, and design notes
- `notes/`: local research notes
- `upstream/Purpur/`: upstream Purpur source and BonfireHearth core changes

### Release Direction

BonfireHearth currently targets a production-ready first release line built around the validated RC1 furnace workflow.

- Purpur compatibility first
- targeted runtime budgeting instead of Folia-style deep thread rewrites
- measurable optimization for Bonfire business workloads
- patch-backed, rebuildable source delivery

### Stage Log

#### 2026-04-17 RC1 Release Packaging And Public Release Notes

- Expanded the repository-level release explanation from engineering-only wording into a user-facing release summary.
- Added a clearer description of what BonfireHearth optimizes, how the gains are achieved, and what the validated local project-side results currently look like against the earlier Purpur baseline.
- Prepared the `1.21.8 RC1` release package and release-facing artifact metadata for GitHub distribution.

#### 2026-04-17 Open-Source License Alignment

- Removed the previous non-commercial and written-contact restriction from the repository-level documentation.
- Added a root-level `MIT License` for BonfireHearth to align the project with a mainstream pure open-source distribution model.
- Switched the root repository README to a single-page bilingual layout with in-page language anchors.
- Kept upstream licensing boundaries explicit: `upstream/Purpur/` still retains its original license notices and copyright statements.

### License

BonfireHearth is released under the [MIT License](LICENSE).

Upstream code under `upstream/Purpur/` keeps its original license notices.
Please keep existing upstream notices intact when redistributing or modifying the source tree.

---

## 简体中文

BonfireHearth 是 Bonfire 自有的 Purpur 分支核心，面向重插件 Minecraft 生产服务器。

它的目标不是做一个低兼容性的实验核心，而是在尽量保持 Paper/Purpur 插件兼容性的前提下，为篝火业务场景补上更针对性的运行时优化能力，包括实体压力、同步调度压力、启动洪峰、低内存观测，以及偏业务服方向的原版自然刷怪关闭能力。

### 项目定位

- 项目代号：`炉心`
- 上游基础：`Purpur`
- 当前目标版本线：`1.21.8`
- 核心目标：在保持高 Paper/Purpur API 兼容性的同时，提升篝火式重插件大世界的真实承载能力

### 为什么会有这个项目

篝火的生产环境并不是原版生存服。
它是一个高插件密度、高实体密度、高模型负载、高同步压力的大世界业务环境。

BonfireHearth 的存在意义，就是在不把核心改造成低兼容实验分支的前提下，针对这类真实负载做工程化优化。

### 当前已经覆盖的优化方向

- 实体追踪与同步预算
- 低动态 LivingEntity 发包节流
- 低优先级空闲 Mob AI 预算
- 同步调度预算与热点插件诊断
- 区块发送预算
- 区块加载与生成准入控制
- 网络压力诊断
- 启动保护与暖机压平
- 低内存观测钩子
- 面向 Bonfire 业务服的原版自然刷怪链关闭

### 炉心到底优化了什么

炉心并不是靠一次大规模线程模型重写来换性能。
它真正的价值，是在尽量保留 Purpur / Paper 插件兼容面的前提下，对重插件业务服最容易炸的几条链路做“兼容优先的负载整形”：

- 降低高密度低动态实体与模型载体带来的同步发包压力
- 压平服务器启动后第一分钟的插件同步洪峰
- 在区块发送、区块加载、区块生成这些位置提前做准入与预算控制
- 对不需要原版自然刷怪的业务服，直接裁掉无意义的 vanilla 负担
- 保留尽可能高的 Purpur / Paper API 兼容性，而不是把核心改成难以接插件的实验分支

### 目前已经测出来的实际结果

下面这些数字来自 Bonfire 自己的灰度与 RC 验证过程，属于项目内实测结果，不应该被当成“所有服务器都能直接照搬”的通用跑分：

- 在炉心立项前，Bonfire 的生产基线是一个已经做过优化的 Purpur 重插件环境，约 `130` 个插件、其中约 `20` 个属于大型核心插件；这种情况下大约只能撑到 `50` 人左右就开始明显掉刻。
- 在本地 MythicMobs / 重模型生物场景里，早期基线大约在 `100` 只左右就会进入比较明显的不健康区；炉心灰度线把更严重的恶化点推到了大约 `140` 只。
- 在后续本地验证里，`160+` 只 MM 生物场景仍能保持“可继续测试、没有直接失控”的状态，但更高密度场景依旧会明显拉高网络压力和内存压力。
- 在 `-Xmx2G` 条件下，内存从大约 `100` 只高密度生物就开始明显爆红，推迟到了大约 `140` 左右才进入更不健康区间。

### 我们是怎么做到的

炉心目前的收益，主要不是来自“把 Minecraft 改成另一个线程模型”，而是来自一组更保守、更兼容的运行时控制策略：

- 用数据包级别的同步节流，专门压低低动态实体的视觉类发包，而不是粗暴地砍掉整类实体更新
- 给实体追踪、Mob AI、同步调度、区块发送、区块加载这些热点都加上预算与准入控制
- 在开服后最危险的第一分钟启用 startup guard，把非关键同步任务做软延后，避免插件洪峰直接把 TPS 打穿
- 用低内存观测和业务服快速路径，避免继续为不需要的原版系统浪费预算
- 用网络压力、热点插件、同步压力等诊断输出来做有依据的调优，而不是盲调

### 当前推荐版本

- 推荐版本：`1.21.8 RC1`
- 发布包：`BonfireHearth-Core-1.21.8-rc1-mojmap.jar`
- SHA256：`EB6D60C143A3BBDEBD750BA027B2A57EC67A214A367296E04B9C987934759645`
- 构建版本线：`1.21.8-R0.1-BONFIRE-RC.1`
- 本地烟雾验证：真实炉心测试栈已启动到 `Done (71.145s)!`

### 工作区结构

- `docs/`：规划、路线图、设计说明
- `notes/`：本地研究记录
- `upstream/Purpur/`：Purpur 上游源码与 BonfireHearth 的核心改动

### 当前发布方向

BonfireHearth 目前以已经验证过的 RC1 炉心流程为第一条成品化发布线，整体方向保持不变：

- 优先保持 Purpur 兼容性
- 优先做针对性预算与准入控制，而不是直接走 Folia 式深线程模型重写
- 优先服务篝火业务负载，而不是泛化成所有类型服务器的实验核心
- 所有核心改动都尽量保持为可追溯、可重建的补丁化源码交付

### 阶段记录

#### 2026-04-17 RC1 发布包与成品说明补完

- 把仓库首页从“偏工程内部说明”补成了面向服主与使用者的成品化说明。
- 增加了炉心相对 Purpur 的核心优化方向、当前已测出的项目内实测结果，以及背后的主要技术实现思路。
- 准备将 `1.21.8 RC1` 成品包连同校验信息一起发布到 GitHub Release。

#### 2026-04-17 开源授权对齐

- 移除了仓库级文档中原先的非商用与书面联系限制。
- 在根目录新增 `MIT License`，将 BonfireHearth 调整为主流纯开源分发模式。
- 将根仓库 README 调整为单页双语锚点结构，点击后可直接在同页跳转到完整语言说明。
- 明确保留上游授权边界：`upstream/Purpur/` 继续沿用其原始许可证与版权声明。

### 开源授权

BonfireHearth 采用 [MIT License](LICENSE) 开源。

`upstream/Purpur/` 中的上游代码继续保留其原始许可证与版权声明；在分发或修改源码时，请保留这些既有声明。
