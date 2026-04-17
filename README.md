# BonfireHearth

BonfireHearth 是 Bonfire 项目的定制 Purpur 服务器核心工作区，项目代号 `炉心`。

> 非商用。商用、二次商用或闭源分发请先书面联系：`mingxi7707@qq.com`

BonfireHearth is the custom server-core workspace for the Bonfire project.

- Chinese codename: `炉心`
- Base upstream: `Purpur`
- First target line: `1.21.8`
- Goal: keep high Paper/Purpur plugin compatibility while adding Bonfire-specific performance optimizations

## Why this exists

The Bonfire production server is a heavy-plugin world with high entity, model, packet, and scheduler pressure.

We do not want to turn Purpur into a half-broken Folia clone.
We do want a Bonfire-owned core fork that can:

- keep plugin compatibility as high as possible
- add targeted optimizations for Bonfire hotspots
- expose small internal hooks for Bonfire plugins
- stay maintainable across upstream rebases

## Workspace layout

`docs/`
- planning, roadmap, design notes

`notes/`
- temporary local research notes

`upstream/Purpur/`
- upstream Purpur source clone
- current branch target: `ver/1.21.8`

## First optimization themes

1. Entity tracking and visibility budgeting
2. Pathfinding and AI budgeting
3. Plugin sync-task budgeting
4. Chunk load/send admission control
5. Packet coalescing and Bonfire internal hooks

## Current policy

- Keep thread-model changes out of the first phase
- Prefer budgeting, throttling, deduplication, and fast-path hooks
- Measure each change against Bonfire-specific pressure scenes
- Re-evaluate Folia/Canvas only after Bonfire-specific Purpur gains are proven insufficient

## Next step

Read [docs/ROADMAP.md](docs/ROADMAP.md) and start with the first implementation slice:
entity tracking and visibility budgeting.

## Stage Log

### 2026-04-17 RC1 Release Convergence

- Froze the first BonfireHearth release scope around the current Gray.4-validated core path instead of continuing business-pack expansion.
- Promoted the runtime branding and build version from the rolling `Gray.4` work channel into the first release-candidate channel.
- Re-ran compile and bundler packaging against the current source tree to confirm the candidate can still be reproduced from source instead of relying on an old handoff jar.
- Locked the release focus to a Bonfire business-server core: high Paper/Purpur plugin compatibility, Bonfire branding, pressure budgeting, observability, startup protection, low-memory monitoring, and vanilla natural-spawn shutdown.
- Shifted the project state from ongoing gray development into first release-candidate packaging and final acceptance.
- Synced the first RC1 handoff jar into the active `7.0` furnace test-server path and updated the launcher entry to point at the RC1 package instead of the older Gray.4 hotfix line.
- Repaired the live `littleroom Horse Pack` `horse_black.yml` YAML breakage that was blocking Mythic from registering `LRDH_horse_black` during RC1 startup validation.

### 2026-04-16 Gray.4 Runtime Hotfix

- Unified the littleroom horse pack runtime compatibility layer on the live gray server.
- Replaced noisy `blocktype` horse conditions with `onblock` equivalents that hot-reload cleanly on MythicMobs `5.11.2`.
- Added `LRD_ -> LRDH_` step-script compatibility aliases across the horse pack so BetterModel / ModelEngine callbacks stop missing Mythic skill entries during runtime.
- Fixed the local mixed-wave load tool so single-bucket spawns no longer crash under PowerShell strict mode.
- Identified a separate BonfirePets runtime issue: its config in the `7.0` gray server was still pointing at legacy `5.0` plugin directories. The config was corrected, but that part still needs a plugin reinitialization or server restart before it can be considered validated.
- Added a live compatibility mob alias for `BFP_Horse_Bay`, then verified that `BonfirePets` can now respawn the active horse pet on the gray server without the previous null-summon failure.
- Fixed the `BonfirePets` `debug tracker` path so it no longer runs entity / Bukkit tracker inspection from an async scheduler thread.
- Verified on the live gray server that the `debug tracker` command now completes without `AsyncCatcher` main-thread violations.
- Hot-patched `CodexMCPetsHotfix` on the gray server to remove the obvious startup `mm reload` command dependency, then confirmed there is still a second delayed Mythic reload chain elsewhere in the live plugin stack that needs separate tracing.
- Tuned the live gray mixed-wave injector to use smaller per-bucket spawn counts and a wider layered ring so high-count horse waves stop self-overlapping into suffocation deaths during local load tests.

### 2026-04-16 Gray.4 Player Network Probe

- Added a Bonfire per-player network probe on top of the existing network-pressure accounting path.
- The probe rolls up one-second windows for each active player and logs combined `bytes/s`, entity-sync `bytes/s`, chunk-send `bytes/s`, peak tick bytes, tracked-entity peak, and packet-kind breakdowns.
- The new probe is gated behind `settings.bonfire.network-pressure.player-probe.*` so gray validation can measure real single-player observation cost without depending on external packet capture tooling.
- Wired the probe and pressure diagnostics into the real `MinecraftServer` tick loop so the runtime gray server can now emit those measurements during live load scenes instead of only compiling them into the jar.

### 2026-04-16 Gray.4 Player Network Probe Packet Wiring

- Confirmed that the first player-probe rollout still lacked real packet-path call sites, so live gray validation could not produce trustworthy `bytes/s` output yet.
- Wired `ServerEntity` broadcast paths into `BonfireNetworkPressure` so entity metadata, equipment, head-rotation, movement, and bundled sync packets now enter the per-player probe.
- Wired `PlayerChunkSender.sendNextChunks(...)` into `BonfireNetworkPressure.recordChunkBatch(...)` so chunk-batch pressure and estimated chunk bytes are recorded from the actual runtime send path.
- Re-verified `:purpur-server:compileJava` after the packet-path wiring so the next gray artifact can finally produce real single-player bandwidth logs during MM/model observation tests.

### 2026-04-16 Gray.4 Static Model Sync Budget Activation

- Confirmed that `BonfireEntitySyncBudget` already existed for static living entities, but the main `ChunkMap` tracker loop was still calling only the older visual-entity budget path.
- Rewired both active `ChunkMap` `serverEntity.sendChanges()` call sites to go through `BonfireEntitySyncBudget.shouldSendChanges(...)` instead of bypassing the static living-entity budget.
- This makes the existing Bonfire sync budget finally apply to the real heavy-model carrier class of problems: low-dynamic `LivingEntity`-based mobs that keep a high Paper-compatible plugin surface while still generating expensive sync churn.
- Re-verified `:purpur-server:compileJava` after the tracker-loop activation so the next runtime build can actually benefit from the already-configured static sync intervals.

### 2026-04-16 Gray.4 Packet-Level Static Sync Gate

- Refined the static living-entity budget from a coarse whole-entity gate into a packet-level gate aimed at heavy model engines.
- `ChunkMap` now stays on the older visual-entity tracker budget, while `ServerEntity` uses `BonfireEntitySyncBudget.shouldBroadcastPacket(...)` right before broadcast.
- The packet-level gate throttles only the high-churn visual/state sync classes for budgetable static living entities: entity data, equipment, head rotation, and attribute updates.
- Movement-critical packets are intentionally left outside this gate, so the Bonfire path is now closer to the intended rule set for MEG / BetterModel style stacks: reduce visual sync spam without blindly suppressing core movement semantics.

### 2026-04-16 Gray.4 Packet-Type Tuning And Density Gate

- Split the packet-level static sync gate into per-packet-type intervals so metadata, equipment, head rotation, and attribute packets no longer share one blunt throttle cadence.
- Added a minimum tracked-player density gate before the packet-level throttle activates in normal network conditions, reducing the chance that low-count scenes get penalized too early.
- The new defaults intentionally bias toward lighter metadata throttling and heavier equipment / head-rotation / attribute throttling, matching the observed risk profile of model-engine visual churn better than the earlier one-size-fits-all interval.
- Re-verified `:purpur-server:compileJava` after the per-packet tuning pass so the next runtime build can use differentiated packet budgets instead of a single shared interval.

### 2026-04-16 Gray.4 Sync Phase Staggering And Far-Viewer Boost

- Added phase staggering to both the legacy visual-entity tracking budget and the static living-entity sync budget, so throttled entities no longer tend to flush on the same tick boundaries.
- Added a far-viewer-aware interval boost to the packet-level sync gate: if every tracked viewer is beyond the configured near scene, Bonfire now pushes the visual/state packets a little less often.
- The result is a more business-oriented sync path for heavy model engines: packet kind aware, crowd aware, distance aware, and phase staggered instead of relying on one shared modulo cadence.
- Re-verified `:purpur-server:compileJava` after the far-viewer and phase-stagger pass so the current worktree stays in a compile-green state.

### 2026-04-16 Gray.4 Model Sync Hint Hook

- Added a low-cost collaboration hook for business entities: plugins, skills, or scripts can now steer Bonfire sync behavior using only entity scoreboard tags instead of a dedicated plugin API.
- Supported tags in the first pass are:
  - `bonfire:model-static` / `bonfire:static-model`
  - `bonfire:model-heavy` / `bonfire:heavy-model` / `bonfire:model-budget-heavy`
  - `bonfire:model-near-priority` / `bonfire:sync-priority` / `bonfire:model-sync-priority`
- Static/heavy hints relax the strict stationary gate and allow stronger packet-budget treatment for model-heavy entities; near-priority hints keep nearby showcase entities from being pushed into extra distance-based throttling too early.
- Re-verified `:purpur-server:compileJava` after adding the hint hook so Bonfire now has a concrete plugin-side coordination entry point for the next stage of model-engine tuning.

### 2026-04-16 Gray.4 Mythic Pack Audit Tooling

- Added a Bonfire Mythic model-sync hint guide under `docs/` so business integration does not live only in chat history.
- Added a live server audit script that scans MythicMobs pack mob files, detects model-heavy signals, checks existing Bonfire tags, and suggests the right hint tags.
- Verified the audit script against the current littleroom horse pack; all active horse mobs were flagged as clear `bonfire:model-heavy` candidates with conditional `bonfire:model-near-priority` for mounted/showcase scenes.

### 2026-04-16 Gray.4 Mythic Hint Snippet Generator

- Added a second live-server tool that turns the audit result into ready-to-paste MythicMobs snippet output instead of leaving the rollout stuck at an analysis-only stage.
- The generator emits per-file guidance, real mob ids, default `bonfire:model-heavy` spawn/load lines, and optional `bonfire:model-near-priority` enter/exit hooks for mounted or showcase phases.
- Verified the syntax direction against the installed MythicMobs `5.11.2` mechanics and documented the supported `tagadd` / `removetag` aliases so the Bonfire-to-business cooperation layer is now implementation-ready.

### 2026-04-16 Gray.4 Hint Adoption Diagnostics

- Extended the Bonfire pressure diagnostics so live logs now show how many entities in each world currently carry `heavy`, `static`, and `near-priority` sync hints.
- Added `top-hinted-entity-types` output next to the existing world pressure summary, making it much easier to confirm whether business-pack rollout is actually reaching the entities that dominate model-sync pressure.
- This closes the observability gap between "core supports hints" and "runtime packs are really using hints", which is important for the next stage of targeted MM / model-engine convergence.

### 2026-04-16 Gray.4 Safe Mythic Heavy-Hint Rollout Helper

- Added a live-server rollout helper that can apply the safest part of the Bonfire hint strategy directly to Mythic mob files: the default `bonfire:model-heavy` spawn/load lines.
- The helper is `dry-run` by default, groups changes by file and mob id, and writes timestamped backups before touching pack files when `-Apply` is used.
- Dynamic `bonfire:model-near-priority` remains intentionally manual, so we can automate the low-risk baseline first without pretending mount/showcase lifecycle semantics are generic.

### 2026-04-16 Gray.4 First Live Horse-Pack Heavy-Hint Rollout

- Ran the safe rollout helper against the current littleroom horse pack on the gray server and applied the baseline `bonfire:model-heavy` tags to the active horse mob files.
- The rollout created timestamped per-file backups, so the current business-pack convergence step is reversible without guessing or hand-rebuilding the original pack state.
- Tightened the audit script to ignore `.bonfire-backup-*` files afterward, preventing backup artifacts from polluting later hint-adoption reports and rollout planning.

### 2026-04-16 Gray.4 Mythic Hint Adoption Status Helper

- Added a pack-level status helper that turns the raw audit json into a compact rollout view: files audited, heavy-baseline adoption count, near-priority candidate count, and top timer-load entries per pack.
- This gives Bonfire a lightweight control panel for the next waves of business-pack convergence, instead of relying on ad-hoc terminal inspection of the raw audit output.

### 2026-04-16 Gray.4 Horse Near-Priority Lifecycle Helper

- Added a horse-pack-specific rollout helper for the current littleroom mount flow, wiring `bonfire:model-near-priority` into real mount entry paths and removing it on dismount/death cleanup paths.
- Upgraded the audit script to also inspect the matching `skills/` file beside each mob file, so Bonfire status reports can now see near-priority tags that live in lifecycle skills instead of only in the mob definition itself.

### 2026-04-16 Gray.4 Near-Priority Core Relief

- Refined the Bonfire packet-budget path so `bonfire:model-near-priority` now does a little more than disable the far-viewer penalty.
- Near-priority entities now get a one-step interval relief on the visual/state packet classes that matter most to mounted or showcased model carriers: metadata, equipment, and head rotation.
- The rule stays intentionally conservative: attributes are not exempted, and the entity still stays inside the Bonfire budget system instead of bypassing it entirely.

### 2026-04-16 Gray.4 First Live Horse Near-Priority Rollout

- Applied the horse-pack near-priority lifecycle helper to the current littleroom horse pack, wiring `bonfire:model-near-priority` into the real mount entry paths and removing it on dismount/death cleanup paths.
- Re-ran the audit, snippet, and status generators afterward; the current horse pack now reports `12/12` heavy baseline adoption and `12/12` near-priority adoption in the Bonfire rollout status view.

### 2026-04-17 RC1 Release Convergence

- Promoted the validated Gray.4 furnace workline into the first packaged RC candidate by aligning the upstream version to `1.21.8-R0.1-BONFIRE-RC.1` and the Bonfire branding channel to `Hearth-RC.1`.
- Rebuilt and staged the first RC bundle as `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`, refreshed the gray artifact notes, and switched the local furnace test server over to the RC path for smoke validation.
- Repaired the live `littleroom Horse Pack` `horse_black.yml` YAML breakage so Mythic returns to `Loaded 22 mobs` instead of failing on `LRDH_horse_black` during the RC smoke start.

### 2026-04-17 RC1 Hotfix 1 Runtime Version Cleanup

- Prototyped an `rc1-hotfix1` runtime-version cleanup path that successfully replaced the visible `DEV` string with `1.21.8-1-Hearth-RC.1` in startup and `/version`.
- Confirmed that post-build jar surgery is not yet safe for furnace release use: the artifact-level manifest/bundler mutation path regressed Paper mapping bootstrap and triggered `ObfHelper` / missing-mappings startup failures.
- Rolled the local furnace test server back to the stable `BonfireHearth-Core-1.21.8-rc1-mojmap.jar` baseline after validation, so the remaining runtime-version cleanup work is now clearly classified as a source-level follow-up rather than a shippable hotfix.

### 2026-04-17 RC1 Source Tree Recovery And Rebuild

- Restored the accidentally drifted `purpur-server/paper-patches/files` baseline and aborted the half-finished `paper-server` patch-apply session, bringing the BonfireHearth source tree back to a trustworthy patch-backed state.
- Repaired the remaining Java 21 compatibility hunk in `Holderable` and corrected `ServerBuildInfoImpl` to use the Bonfire runtime brand source instead of the stale broken `Purpur` identifier path.
- Re-verified the core delivery chain end to end: `:purpur-server:applyAllServerPatches`, `:purpur-server:compileJava`, and `:purpur-server:createMojmapBundlerJar --rerun-tasks` all completed successfully again on the BonfireHearth worktree.
- Rebuilt and replaced the packaged `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`, copied it back into the furnace artifacts folder and live local test directory, then smoke-started the real plugin stack to `Done (71.145s)!`.
- The remaining startup noise in the gray server logs is now clearly separated from the core rebuild work: the dominant residual errors come from the live Mythic/ModelEngine business stack, not from the BonfireHearth source tree failing to apply, compile, or boot.
