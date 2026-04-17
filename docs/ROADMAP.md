# BonfireHearth Roadmap

## Phase 0

Set the baseline before any core patch:

- prepare 3 to 5 repeatable Bonfire pressure scenes
- record MSPT, TPS, entity counts, packet volume, chunk send/load rates
- identify top sync task sources and top entity types

## Phase 1

Implement the first low-risk, high-yield core patches:

1. Entity tracking and visibility budgeting
2. Pathfinding and AI budgeting
3. Plugin sync-task budgeting

Success target:

- push the first visible lag line above the current Purpur baseline
- turn large spikes into controlled degradation

## Phase 2

Expand into world-delivery optimization:

4. Chunk load/send admission control
5. Packet coalescing
6. Bonfire internal server hooks for self-owned plugins

## Phase 3

Rebase and productize:

- keep patch set small and readable
- test against live Bonfire plugin stack
- verify startup behavior, not only runtime MSPT
- decide whether BonfireHearth is enough or whether a Folia-family migration is still needed

## Immediate implementation entry

Start with these concrete goals:

1. classify low-priority visual entities such as display-like and cosmetic entities
2. add dirty-check and staggered update gates where safe
3. expose pressure-state hints that Bonfire plugins can consume later
