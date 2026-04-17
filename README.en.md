# BonfireHearth

BonfireHearth is a Bonfire-owned Purpur fork for heavy-plugin Minecraft production servers.

It keeps Paper/Purpur plugin compatibility as high as practical while adding Bonfire-specific runtime optimizations for entity pressure, scheduler pressure, startup spikes, low-memory observation, and business-server-oriented vanilla spawn shutdown.

## Project Position

- Codename: `炉心`
- Base upstream: `Purpur`
- Current target line: `1.21.8`
- Primary goal: keep high Paper/Purpur API compatibility while improving real-world throughput for Bonfire-style heavy-plugin worlds

## Why It Exists

The Bonfire production environment is not a vanilla survival server.
It is a large plugin-driven world with dense entities, model engines, scheduler hotspots, and packet pressure.

BonfireHearth exists to optimize that workload without turning the core into a low-compatibility experimental branch.

## Current Optimization Scope

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

## Workspace Layout

- `docs/`: planning, roadmap, and design notes
- `notes/`: local research notes
- `upstream/Purpur/`: upstream Purpur source and BonfireHearth core changes

## Release Direction

BonfireHearth currently targets a production-ready first release line built around the validated RC1 furnace workflow.
The project direction remains:

- Purpur compatibility first
- targeted runtime budgeting instead of Folia-style deep thread rewrites
- measurable optimization for Bonfire business workloads
- patch-backed, rebuildable source delivery

## Stage Log

### 2026-04-17 Open-Source License Alignment

- Removed the previous non-commercial and written-contact restriction from the repository-level documentation.
- Added a root-level `MIT License` for BonfireHearth to align the project with a mainstream pure open-source distribution model.
- Split the repository documentation into dedicated English and Simplified Chinese README files.
- Kept upstream licensing boundaries explicit: `upstream/Purpur/` still retains its original license notices and copyright statements.

## License

BonfireHearth is released under the [MIT License](LICENSE).

Upstream code under `upstream/Purpur/` keeps its original license notices.
Please keep existing upstream notices intact when redistributing or modifying the source tree.
