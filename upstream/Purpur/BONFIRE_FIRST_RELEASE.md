# BonfireHearth RC1 Release Guide

Current furnace handoff artifact: `BonfireHearth-Core-1.21.8-rc1-mojmap.jar`

## Scope

This document defines the first BonfireHearth release-candidate baseline for the Bonfire production stack.
The goal of RC1 is not to invent a new thread model, but to make a heavy-plugin Purpur fork safer under pressure, easier to observe, easier to gray-release, and ready for first packaged delivery.

## RC1 Product Boundary

RC1 is the first Bonfire business-server candidate, not a generic survival-server fork.

The intended release shape is:

- keep Paper/Purpur plugin compatibility as high as possible
- keep Bonfire branding and version identity consistent across runtime surfaces
- ship Bonfire pressure budgeting, observability, startup guard, low-memory monitoring, and vanilla natural-spawn shutdown
- avoid deep thread-model rewrites before the first production-facing candidate is stabilized

## Recommended Default Config

### Global `purpur.yml`

```yaml
settings:
  bonfire:
    sync-task-budget:
      enabled: true
      defer-repeating-tasks: true
      defer-only-period-1: true
      log-task-overruns: true
      log-deferrals: true
      log-top-plugins: true
      log-cooldown-ticks: 200
      top-plugins-to-log: 3
      exempt-plugins: []
      thresholds:
        warn-task-ms: 8.0
        summary-min-runtime-ms: 10.0
        high-pressure-mspt: 40.0
        extreme-pressure-mspt: 47.0
        high-pressure-tick-budget-ms: 12.0
        extreme-pressure-tick-budget-ms: 6.0
    pressure-diagnostics:
      enabled: true
      log-cooldown-ticks: 200
      min-players: 1
      top-entity-types-to-log: 5
      thresholds:
        high-pressure-mspt: 40.0
        extreme-pressure-mspt: 47.0
```

### World `purpur.yml`

```yaml
settings:
  bonfire:
    entity-tracking-budget:
      enabled: true
      intervals:
        high-pressure: 2
        extreme-pressure: 4
      thresholds:
        high-pressure-mspt: 40.0
        extreme-pressure-mspt: 47.0
        crowd-tracked-players: 12
      entities:
        display: true
        interaction: true
        marker-armor-stand: true
        invisible-no-gravity-armor-stand: true
    mob-ai-budget:
      enabled: true
      goal-selector-intervals:
        high-pressure: 3
        extreme-pressure: 5
    chunk-send-budget:
      enabled: true
      thresholds:
        high-pressure-mspt: 40.0
        extreme-pressure-mspt: 47.0
      limits:
        high-pressure-max-chunks-per-tick: 6.0
        extreme-pressure-max-chunks-per-tick: 3.0
        high-pressure-max-unacknowledged-batches: 6
        extreme-pressure-max-unacknowledged-batches: 3
    chunk-load-budget:
      enabled: true
      thresholds:
        high-pressure-mspt: 40.0
        extreme-pressure-mspt: 47.0
      rates:
        high-pressure-load-rate-multiplier: 0.75
        extreme-pressure-load-rate-multiplier: 0.35
        high-pressure-gen-rate-multiplier: 0.50
        extreme-pressure-gen-rate-multiplier: 0.20
      limits:
        high-pressure-max-concurrent-loads: 24
        extreme-pressure-max-concurrent-loads: 12
        high-pressure-max-concurrent-generates: 8
        extreme-pressure-max-concurrent-generates: 4
```

## Compatibility Notes

### Display / model-heavy plugins

Plugins that build visuals from `Display`, `Interaction`, `ArmorStand`, or marker armor stands can show reduced sync frequency under pressure.
Typical examples in the Bonfire stack are ModelEngine-style visual models, ItemsAdder-style decorative entities, and custom hologram / furniture systems.

Recommended handling:

- Keep diagnostics enabled so the hot world and hot entity types are visible in logs.
- If players report visible desync first, relax `entity-tracking-budget` before disabling unrelated budgets.
- For event worlds or showcase areas, consider loosening only the display / interaction throttles instead of disabling the whole budget family.

### AI-heavy scripted mobs / pets / NPC ecosystems

`mob-ai-budget` only slows full goal-selector / target-selector cadence for relatively low-risk mobs: no current target, not recently hurt, not leashed, not mounted, not being ridden.
That is intentionally safer than patching navigation directly, but some custom mob ecosystems can still be sensitive.

Typical risk areas:

- MythicMobs idle skill loops that rely on frequent untargeted AI refresh
- pet / companion systems that keep many passive followers loaded nearby
- NPC ecosystems with dense idle crowd scenes

Recommended handling:

- If a specific world depends on idle scripted mob precision, lower the goal-selector intervals there first.
- If one plugin still misbehaves, disable `mob-ai-budget` only in the affected world rather than rolling back all Bonfire budgets.

### Scheduler-sensitive plugins

`sync-task-budget` can soft-defer repeating sync tasks by one tick under pressure.
That protects MSPT when many `period=1` tasks pile up, but it is not free.

Typical risk areas:

- menu / UI plugins that expect exact every-tick animation cadence
- combat / minigame systems with tight tick timing assumptions
- plugins that already run too much work in sync repeaters

Recommended handling:

- Use `exempt-plugins` sparingly for plugins whose cadence is semantically critical.
- Do not exempt large plugin groups blindly, or the budget loses its value.
- Keep top-runtime / top-deferred logging enabled during gray-release so repeat offenders surface quickly.

### Chunk burst workloads

`chunk-send-budget` and `chunk-load-budget` deliberately trade burst speed for survival under pressure.
That means first-login bursts, mass exploration, or forced pregen-like traffic can feel slower before they feel faster.

Recommended handling:

- Judge these budgets by sustained MSPT and spike reduction, not by raw burst speed alone.
- Only loosen the chunk rates after the first gray-release proves there is safe headroom.

## Gray Release Checklist

Run the first gray-release on a production-like copy of the Bonfire stack.
Keep plugin list, world data, and JVM flags as close to production as possible.

### Required scenarios

1. Main city / lobby / activity zone with dense displays, armor stands, pets, and menus
2. Multi-player exploration with new chunk loading and generation
3. AI-heavy areas with custom mobs, pets, and idle crowds
4. Login bursts plus common command / menu interactions

### Record on each run

- average MSPT and sustained high-MSPT windows
- scheduler pressure logs
- pressure diagnostics world summaries
- player-visible regressions such as delayed model sync, sluggish idle mobs, or delayed menu actions
- whether any plugin needs temporary exemption from the sync-task budget

### Promotion gate

BonfireHearth RC1 is ready to promote only if all of the following are true:

- no crashes or world data corruption
- no unacceptable gameplay regression in main activity worlds
- sustained high-pressure MSPT is materially better than the current production Purpur baseline
- hot worlds / hot entity types / hot plugins are observable directly from logs

## Rollback Order

If a gray-release goes wrong, roll back from the narrowest lever first.

1. Disable `settings.bonfire.pressure-diagnostics` if the issue is only log volume.
2. Add a specific plugin to `settings.bonfire.sync-task-budget.exempt-plugins` if the issue is exact sync cadence.
3. Relax or disable `mob-ai-budget` only in affected worlds if scripted idle mobs regress.
4. Relax `entity-tracking-budget` entity toggles for model-heavy worlds if visual sync is the main complaint.
5. Relax chunk send / load limits if the complaint is burst exploration speed and MSPT headroom allows it.
6. Swap back to the previous Purpur production jar if the issue is broad or unclear.

Always keep the last known-good Purpur jar and matching config set available during the first BonfireHearth gray-release.
