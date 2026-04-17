# BonfireHearth First Release TODO

## Goal

Push BonfireHearth to a first complete release candidate that is:

- buildable
- patch-backed
- configurable
- observable
- safe enough for gray-release testing on the Bonfire production stack

## Completed

- Java 21 compatibility fixes are patch-backed
- Entity tracking budget
- Mob AI budget
- Sync scheduler budget
- Chunk send budget
- Chunk load/generation admission control
- Scheduler observability upgrade for plugin runtime/deferral summaries
- World pressure diagnostics for entity/model-heavy regions
- First-release defaults, compatibility guardrails, and rollback guide
- Gray-release record template prepared
- Server-ready mojmap bundler jar built
- Smoke startup validation reached Done on the production-like plugin stack
- Validation probe compatibility scan completed on the production-like plugin stack
- Short idle stability validation completed on the production-like plugin stack
- Repeatable gray idle-soak runner prepared and verified
- Gray log auto-analysis prepared and verified
- BonfireHearth Core runtime branding and gray version packaging completed
- Network-pressure accounting and static entity-sync budgeting completed
- Bonfire low-memory monitoring and vanilla natural spawning shutdown completed
- Gray.4 forced Bonfire branding and runtime config migration completed
- Gray.4 raw/effective network-pressure split, hysteresis, and packet-kind diagnostics completed
- Gray.4 static living-entity sync gating tightened for low-count stability completed
- Gray.4 compile recovery completed and `:purpur-server:compileJava` is green again on the BonfireHearth source tree
- Gray.4 server-ready mojmap bundler jar rebuilt after compile recovery and copied into the gray artifact handoff folder
- Gray.4 startup hotfix completed by restoring the missing `purpur-settings` bootstrap option and rebuilding the runnable jar
- Gray.4 plugin-compatibility hotfix completed by restoring Bukkit version metadata fallback and packaging legacy `paper-api` pom.properties compatibility resources back into the API jar
- Gray.4 hotfix3 build convergence completed and a fresh compile-verified handoff jar was published to the gray artifacts folder
- Gray.4 hotfix4 runtime wiring completed for startup guard, low-memory monitoring, vanilla spawning shutdown, cleanup execution, and special-spawn gating
- Gray.4 per-player network probe completed for single-player `bytes/s` peak/average validation
- Gray.4 player-network probe tick-loop wiring completed so live gray builds can actually emit the new per-player bandwidth logs
- Gray.4 player-network probe packet-path wiring completed for `ServerEntity` and `PlayerChunkSender`
- Gray.4 static living-entity sync budget activation completed for both live `ChunkMap` tracker send paths
- Gray.4 packet-level static sync gate completed for heavy model-engine visual/state packets
- Gray.4 per-packet static sync tuning and tracked-density gate completed
- Gray.4 sync phase staggering and far-viewer interval boost completed
- Gray.4 model sync hint hook completed for plugin/script cooperation via scoreboard tags
- Gray.4 Mythic pack audit tooling completed for model-hint rollout planning
- Gray.4 Mythic hint snippet generator completed for non-destructive pack rollout
- Gray.4 hint-adoption diagnostics completed for live rollout verification
- Gray.4 safe Mythic heavy-hint rollout helper completed for dry-run/apply pack convergence
- Gray.4 first live horse-pack heavy-hint baseline rollout completed with audit cleanup for backup-file exclusion
- Gray.4 Mythic hint adoption status helper completed for pack-level rollout tracking
- Gray.4 horse near-priority lifecycle helper completed with related-skill-aware audit support
- Gray.4 near-priority core relief completed for mounted/showcase visual-state packets
- Gray.4 first live horse near-priority rollout completed with 12/12 adoption in the status view
- RC1 release packaging and first artifact handoff completed
- RC1 runtime-version cleanup feasibility tested and narrowed down to a source-level follow-up
- RC1 source-tree recovery completed with green patch-apply, green compile, rebuilt bundler artifact, and live local smoke startup to `Done`
- RC1 first-release verification record and final packaged handoff completed

## In Progress

- Safe runtime-version cleanup without post-build jar mutation regressions
- Full release-grade separation between Bonfire core issues and live Mythic/ModelEngine business-stack residue

## Remaining P0

- None

## Candidate Next Core Work

- Decorative/block-entity/display hot-zone budget
- Safer plugin hotspot surfacing for repeat offenders
- RC1 feedback-driven second-pass tuning
- Formal patch-back convergence for the remaining Bonfire source deltas

## Rules

- Keep focus on core patches, core config, and core observability
- Do not drift into runtime-only tweaking unless it directly validates a core change
- Append each completed stage to `README.md`
- Runtime plugin-pack hotfixes are allowed when they unblock gray validation of the core itself

## Checklist

- [x] Java 21 compatibility repaired inside the patch system
- [x] Entity tracking budget
- [x] Mob AI budget
- [x] Sync scheduler budget
- [x] Chunk send budget
- [x] Chunk load/generation admission control
- [x] Sync task observability
- [x] World pressure diagnostics and hot-spot summaries
- [x] First-release default config recommendations
- [x] Compatibility exemptions and risk notes
- [x] Smoke startup validation completed
- [x] Validation probe compatibility scan completed
- [x] Short idle stability validation completed
- [x] Repeatable gray idle-soak runner prepared and verified
- [x] Gray log auto-analysis prepared and verified
- [x] Network-pressure accounting for entity sync and chunk send paths
- [x] Static low-risk living-entity sync throttling
- [x] Bonfire low-memory monitoring
- [x] Bonfire-mode shutdown of vanilla natural spawning and listed special spawn paths
- [x] Gray.4 hotfix3 compile-verified server-ready handoff artifact
- [x] Gray.4 hotfix4 runtime-wired server-ready handoff artifact
- [x] Gray.4 horse-pack compatibility hotfix validated via live Mythic reload
- [x] Gray.4 strict-mode fix for the mixed-wave local load injector
- [x] Gray.4 BonfirePets tracker-debug async main-thread violation hotfix validated on the live gray server
- [x] Full first gray-release record completed
- [x] First-release rollback notes
