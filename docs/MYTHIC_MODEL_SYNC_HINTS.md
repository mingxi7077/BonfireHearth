# Bonfire Mythic Model Sync Hints

## Purpose

BonfireHearth now supports low-cost model sync hints through entity scoreboard tags.

This is the intended collaboration layer between:

- BonfireHearth core
- MythicMobs skills / mob packs
- ModelEngine / BetterModel / MEG style model stacks

The goal is not to replace plugin-side optimization.
The goal is to let business entities tell Bonfire which mobs are expensive model carriers and which ones must keep better near-view quality.

## Supported Tags

### `bonfire:model-heavy`

Aliases:

- `bonfire:heavy-model`
- `bonfire:model-budget-heavy`

Use this for:

- model-heavy carrier mobs
- mobs with frequent `mestate` / `memodel` / `mesegment` churn
- mounts or showcase entities that are expensive even when they are not fighting

Bonfire effect:

- packet-level sync throttling can activate earlier
- heavy visual/state packets get a slightly stronger interval under pressure

Recommended default:

- most ModelEngine / BetterModel decorative or mount-like entities

### `bonfire:model-static`

Aliases:

- `bonfire:static-model`

Use this only for:

- decorative or mostly idle model entities
- display creatures that spend most of their life grounded and locally stationary

Do not use this by default for:

- fast-moving rideable mounts
- combat mobs with frequent motion-state changes
- entities that should keep high-fidelity sync while active

Bonfire effect:

- the stationary gate is relaxed a bit
- static entities can enter Bonfire's sync budget path more reliably

### `bonfire:model-near-priority`

Aliases:

- `bonfire:sync-priority`
- `bonfire:model-sync-priority`

Use this for:

- showcase mobs that players inspect up close
- the currently mounted or actively interacted entity
- VIP presentation mobs near hubs, menus, lobbies, or sales scenes

Bonfire effect:

- disables the extra far-viewer interval boost for this entity
- helps preserve near-scene quality while the rest of the model crowd still budgets down

Recommended usage:

- add while the mob is actively showcased or mounted
- remove when it returns to background population status

## Practical Recommendations

### For your current horse pack

Default recommendation:

- add `bonfire:model-heavy` to the horse entities

Conditional recommendation:

- add `bonfire:model-near-priority` only to the horse currently being ridden or actively presented

Usually avoid:

- permanent `bonfire:model-static` on the whole horse pack

Reason:

- the horse pack has obvious heavy model signals
- it also has interaction, steering, movement, and mount logic
- that means "heavy" is a strong fit, but "static" should be reserved for truly idle display uses

## Suggested Integration Styles

### Style 1: Spawn-time default tag

Use for:

- mobs that are almost always heavy

Idea:

- on spawn / load, add `bonfire:model-heavy`

### Style 2: Dynamic near-priority tag

Use for:

- mounts
- close-up showcase mobs
- active presentation scenes

Idea:

- add `bonfire:model-near-priority` when mounted, interacted with, or moved into a showcase area
- remove it when the entity returns to background use

### Style 3: Static display-only tag

Use for:

- idle showroom creatures
- decorative business mobs with very low motion semantics

Idea:

- add `bonfire:model-static` only for mobs that are deliberately treated as background display actors

## Rollout Order

1. Tag the clearly heavy model carriers with `bonfire:model-heavy`.
2. Add conditional `bonfire:model-near-priority` to high-importance close-view entities.
3. Only after validation, consider `bonfire:model-static` for truly idle display mobs.

## Audit Workflow

Use the audit script:

- `E:\Minecraft\12121\purpur第四版\一、服务器核心区\7.0正式服核心-炉心\server\bonfire-tools\Invoke-BonfireMythicHintAudit.ps1`

What it does:

- scans MythicMobs pack mob files
- detects model-heavy signals such as `memodel`, `mestate`, `mesegment`, `memountmodel`, `ride=true`, and dense timer usage
- checks whether Bonfire hint tags already appear in the file
- prints suggested tags and reasons

Use the snippet generator when you want ready-to-paste Mythic lines:

- `E:\Minecraft\12121\purpur第四版\一、服务器核心区\7.0正式服核心-炉心\server\bonfire-tools\Invoke-BonfireMythicHintSnippet.ps1`

What it does:

- reads the audit result and the real mob ids from the target pack
- generates a markdown file with ready-to-paste `addtag` / `removetag` lines
- keeps the workflow non-destructive so business packs are not rewritten blindly

Confirmed Mythic mechanic aliases on the current production-like stack:

- `tagadd`
- `addtag`
- `addscoreboardtag`
- `tagremove`
- `removetag`
- `removescoreboardtag`

Use the rollout helper when you want the safe baseline applied automatically:

- `E:\Minecraft\12121\purpur第四版\一、服务器核心区\7.0正式服核心-炉心\server\bonfire-tools\Invoke-BonfireMythicHintApply.ps1`

What it does:

- defaults to `dry-run`
- only auto-inserts the low-risk baseline heavy hint lines
- creates timestamped backups when re-run with `-Apply`
- intentionally leaves dynamic `bonfire:model-near-priority` hooks manual

Use the status helper when you want a pack-level adoption report:

- `E:\Minecraft\12121\purpur第四版\一、服务器核心区\7.0正式服核心-炉心\server\bonfire-tools\Invoke-BonfireMythicHintStatus.ps1`

What it does:

- groups the audit result by Mythic pack
- shows how many files already adopted the heavy baseline
- leaves a compact list of which files still need manual `near-priority` thought

Use the horse near-priority helper when you want the current ride lifecycle wired in:

- `E:\Minecraft\12121\purpur第四版\一、服务器核心区\7.0正式服核心-炉心\server\bonfire-tools\Invoke-BonfireHorseNearPriorityApply.ps1`

What it does:

- targets the current littleroom horse pack structure
- inserts `bonfire:model-near-priority` on mount entry paths
- removes it on dismount, MCPets dismount, and death cleanup paths
- defaults to `dry-run` and creates timestamped backups when applied

## Current Boundary

BonfireHearth core can now:

- classify packet types
- gate by crowd density
- gate by nearest viewer distance
- stagger sync phases
- honor explicit model hints

It still cannot infer every business intention automatically.
For best results, business entities should declare their role using these tags.
