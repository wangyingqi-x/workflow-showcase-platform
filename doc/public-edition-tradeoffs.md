# Public Edition Refactor Tradeoffs

## Goal

This repository is a public showcase, not a raw export of an internal project.

The refactor principle was:

> preserve architectural depth, remove publication risk, and keep one honest runnable path.

## What Was Removed

- private Maven coordinates
- bundled internal jars
- internal hosts, credentials, and production-like configuration
- enterprise-only adapters
- interview drafts and operational notes that did not belong in a public repo

## What Was Preserved

- multi-module layout
- engine-centered project narrative
- graph runtime concepts
- node abstraction and routing
- parallel execution demonstration
- end-to-end local demo API

## Why Not Publish the Full Internal App

Publishing the full enterprise-style application would have created three problems:

1. It depended on private infrastructure that an external reader could not reproduce.
2. It included sensitive operational and configuration context.
3. It would have made the repository harder to understand during an interview.

For a public showcase, a smaller but runnable and truthful slice is stronger than a larger but broken export.

## Why Keep `workflow-admin`

The public edition keeps `workflow-admin` as a reserved module boundary even though the original enterprise code was removed.

That is intentional:

- it preserves the original architectural story
- it shows that the project had a separation between runtime core and platform-facing adapters
- it keeps room for future public extensions without changing the repo structure again

## Current Public Scope

Today the repository officially supports:

- local build
- local demo startup
- graph definition inspection
- parallel branch execution
- join-node synchronization
- final output aggregation

## Future Public Improvements

- add more safe node types that do not depend on private services
- provide a lightweight frontend demo page for the showcase API
- add unit tests around node routing and join behavior
- add richer visualization of execution traces
