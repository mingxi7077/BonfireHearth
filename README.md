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

#### 2026-04-17 开源授权对齐

- 移除了仓库级文档中原先的非商用与书面联系限制。
- 在根目录新增 `MIT License`，将 BonfireHearth 调整为主流纯开源分发模式。
- 将根仓库 README 调整为单页双语锚点结构，点击后可直接在同页跳转到完整语言说明。
- 明确保留上游授权边界：`upstream/Purpur/` 继续沿用其原始许可证与版权声明。

### 开源授权

BonfireHearth 采用 [MIT License](LICENSE) 开源。

`upstream/Purpur/` 中的上游代码继续保留其原始许可证与版权声明；在分发或修改源码时，请保留这些既有声明。
