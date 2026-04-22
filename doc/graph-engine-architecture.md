# Graph Engine Architecture

## Purpose

This document explains the public engine slice kept in `workflow-engine`.

The goal of the public edition is not to reproduce every enterprise integration.  
The goal is to preserve the architectural heart of the system so that readers can understand how the workflow runtime is structured and why it matters.

## Runtime Flow

The public demo still follows the same core execution sequence:

1. Load a graph definition into `FlowWsDTO`
2. Flatten node configuration into runtime-friendly `BaseNodeData`
3. Build edge indexes with `EdgeManage`
4. Create a `GraphEngine`
5. Resolve nodes through `NodeFactory`
6. Execute runnable nodes
7. Route downstream nodes
8. Persist variables in `GraphState`
9. Finish at `EndNode`

## Core Types

- `GraphEngine`: owns workflow execution, queue progression, routing, and failure handling
- `GraphEngineFactory`: builds a runnable engine instance from graph data
- `NodeFactory`: maps node types to concrete node implementations
- `BaseNode`: shared execution contract for all node types
- `GraphState`: in-memory variable and chat-context store
- `RunGraphNodeThreadPool`: bounded thread pool for parallel node scheduling
- `EdgeManage`: fast lookup for outgoing and incoming edges

## Why This Matters

The interesting part is not just that a flow can run.  
The interesting part is that the system separates:

- graph definition
- runtime state
- node execution
- scheduling
- result materialization

That separation makes it much easier to grow the platform with new node categories without rewriting the engine itself.

## Public Demo Example

The public flow used in the demo is intentionally small but expressive:

```text
Start
  -> Output A
  -> Output B
Output A + Output B
  -> RunWaitingNode
  -> End
```

This showcases three important engine behaviors:

- branching
- parallel dispatch
- join synchronization

## Public Refactor Choice

In the private codebase, many nodes depended on internal services and infrastructure.  
For the public edition, those integrations were removed first, while the execution engine was kept.

That tradeoff preserves the strongest technical story:

- this is still a real workflow runtime
- the local demo still exercises scheduling and routing
- the repository stays runnable for external readers
