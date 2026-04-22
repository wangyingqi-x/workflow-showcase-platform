# Node System and Interaction Model

## Node Abstraction

The workflow runtime is built around nodes as the unit of execution.

Every node inherits from `BaseNode`, which gives it:

- identity and type
- access to graph state
- access to inbound and outbound edges
- a standard `run` lifecycle
- a callback channel for event reporting

That means the engine does not need to know the business details of each node.  
It only needs to know when a node can run, what it produces, and where it routes next.

## Public Demo Nodes

The public edition keeps four node types:

- `StartNode`
- `OutputNode`
- `RunWaitingNode`
- `EndNode`

These are enough to show the runtime model clearly without depending on private AI services or enterprise infrastructure.

## Responsibilities by Node

### StartNode

- initializes workflow-scoped context
- emits guide text and guide questions
- seeds base variables such as current time and access token placeholder

### OutputNode

- materializes a user-facing message
- can simulate meaningful work with a delay
- writes output into `GraphState`

### RunWaitingNode

- tracks upstream source nodes
- only becomes runnable after all expected predecessors have completed
- acts as the public demo's join barrier

### EndNode

- resolves referenced variables from upstream nodes
- assembles final workflow output fields
- marks the end of the showcase flow

## Interaction and Events

The public demo also keeps a simplified callback model.

`BaseCallback` receives events for:

- workflow start
- workflow end
- workflow error
- node start
- node end
- output emission

In the demo app, `InMemoryDemoCallback` records these events so the API response can show exactly what happened during execution.

## Why This Is Worth Showing

This design demonstrates a clean extension model:

- the engine is generic
- node classes encapsulate behavior
- callbacks expose observability

That is a much stronger system-design discussion than a tightly coupled service method chain.
