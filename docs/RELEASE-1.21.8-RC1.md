# BonfireHearth 1.21.8 RC1

## 中文

`BonfireHearth 1.21.8 RC1` 是炉心当前第一版成品化发布包，对应已经验证过的 Gray.4 收敛工作线。

这版的核心目标很明确：

- 尽量保留 Purpur / Paper 插件兼容面
- 不做 Folia 式深线程模型重写
- 直接针对 Bonfire 这类重插件、大世界、模型生物密集的业务环境做兼容优先优化

### 这版主要优化了什么

- 实体追踪与数据包级同步预算
- 低动态 LivingEntity 的视觉类同步节流
- 低优先级空闲 Mob AI 预算
- 同步调度预算与启动洪峰压平
- 区块发送预算
- 区块加载 / 生成准入控制
- 网络压力诊断
- 低内存观测
- 面向业务服的原版自然刷怪链关闭

### 当前已经测出来的结果

这些数据来自 Bonfire 项目自己的灰度与 RC 验证过程，不是面向所有服务器的通用跑分：

- 在炉心立项前，Bonfire 生产环境是约 `130` 插件、约 `20` 个大型核心插件的 Purpur 重插件栈，大约只能撑到 `50` 人左右就开始明显掉刻。
- 在本地 MythicMobs / 模型生物压力场景中，早期基线大约在 `100` 只左右进入明显不健康区；炉心灰度线把更严重恶化点推到了大约 `140`。
- 后续本地验证中，`160+` 只 MM 生物场景仍然能保持“可继续测试”的状态，但更高密度场景仍会继续抬高网络与内存压力。
- 在 `-Xmx2G` 条件下，内存明显爆红点从大约 `100` 只高密度生物，推迟到了大约 `140` 左右。

### 这些效果是怎么做出来的

炉心目前的提升，主要来自兼容优先的运行时控制策略，而不是大改线程模型：

- 只对低动态实体的视觉类同步做数据包级限流，不粗暴砍掉功能性同步
- 给实体追踪、AI、同步调度、区块发送、区块加载这些热点都加预算与准入控制
- 用 startup guard 压平开服后第一分钟最危险的插件同步洪峰
- 对业务服不需要的原版自然刷怪链直接硬关闭，减少无意义 vanilla 负担
- 通过网络压力、热点插件、同步压力等诊断输出持续收敛问题点

### 构建与校验

- Release package: `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`
- Source line: `1.21.8-R0.1-BONFIRE-RC.1`
- SHA256: `EB6D60C143A3BBDEBD750BA027B2A57EC67A214A367296E04B9C987934759645`
- Build state: patch apply passed, compile passed, bundler build passed
- Runtime smoke validation: local furnace stack reached `Done (71.145s)!`

## English

`BonfireHearth 1.21.8 RC1` is the first packaged release candidate of the BonfireHearth core line, built from the validated Gray.4 convergence path.

### What This Release Optimizes

- entity tracking and packet-level sync budgeting
- visual sync throttling for low-dynamic living entities
- idle mob AI budgeting
- sync scheduler budgeting and startup spike smoothing
- chunk send budgeting
- chunk load and generation admission control
- network-pressure diagnostics
- low-memory observation
- vanilla natural-spawn shutdown for business-mode servers

### Current Project-Side Validation Results

These numbers are Bonfire project validation results rather than a universal benchmark:

- Before BonfireHearth, the Bonfire production baseline was an optimized Purpur stack with about `130` plugins and about `20` major plugins, and visible tick loss started around `50` players.
- In local MythicMobs / model-heavy scenes, earlier baseline behavior became clearly unhealthy around `100` dense mobs; the Bonfire gray line pushed the more serious degradation point to around `140`.
- In later local validation, scenes above `160` MM mobs could still remain meaningfully testable on the same-machine path, although denser scenes still increased network and memory pressure substantially.
- Under `-Xmx2G`, the visibly unhealthy memory-pressure point was also pushed back from roughly `100` dense mobs to roughly `140`.

### How The Gains Are Achieved

The current gains come mainly from compatibility-first runtime control rather than a deep thread-model rewrite:

- packet-level throttling for low-dynamic visual sync instead of bluntly suppressing full entity updates
- budgets and admission control across entity tracking, AI, scheduler, chunk send, and chunk load hotspots
- startup guard logic to soften the dangerous first live minute after boot
- hard shutdown of unwanted vanilla natural-spawn work for Bonfire business-mode servers
- diagnostics for network pressure, hot plugins, and sync pressure so tuning is evidence-driven

### Build And Validation

- Release package: `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`
- Source line: `1.21.8-R0.1-BONFIRE-RC.1`
- SHA256: `EB6D60C143A3BBDEBD750BA027B2A57EC67A214A367296E04B9C987934759645`
- Build state: patch apply passed, compile passed, bundler build passed
- Runtime smoke validation: local furnace stack reached `Done (71.145s)!`
