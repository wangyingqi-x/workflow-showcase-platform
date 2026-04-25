# Agent Studio Demo Guide

## 1. Start The App

From the repository root:

```powershell
mvn -q -DskipTests package
java -jar workflow-web/target/workflow-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=demo
```

Then open:

```text
http://localhost:8080/
```

The homepage redirects to `Agent Studio`.

## 2. Understand The Two Demo Modes

The Studio now has two distinct modes in the chat panel.

### Agent Chat / ReAct

Use this mode when you want to demonstrate:

- tool-aware planning
- MCP integration
- bounded ReAct loops
- trace and tool execution visibility

If no MCP tools are selected, the app falls back to direct chat mode.

### Workflow Orchestration Demo

Use this mode when you want to demonstrate:

- pure node orchestration
- dual conditional branches
- join behavior with `RunWait`
- recovery routing
- final output aggregation through workflow variables

This mode does not require MCP tools or an LLM key.

## 3. Suggested Workflow Mode Prompt

Switch to `Workflow Orchestration Demo` and try:

```text
I want a relaxed Nanjing itinerary and rainy weather is acceptable.
```

You should see:

1. one shared branch
2. one pace branch selected by `Condition`
3. one weather branch selected by `Condition`
4. `RunWait` waiting for the active branches
5. one recovery branch after the join
6. one final output node assembling the result

Watch these panels:

- `Run Result`
- `Reasoning Trace`
- `Timeline`
- `Variables`

## 4. Suggested ReAct Prompt

If you have imported one or more real MCP servers, switch back to `Agent Chat / ReAct` and try:

```text
Plan a 3-day Nanjing trip. Use tools only when needed, and clearly label any missing data.
```

This is a good prompt because it encourages:

- planning before acting
- calling tools only when useful
- explicit handling of missing observations
- final synthesis grounded in tool results

## 5. Dynamic MCP Import

Open the `Dynamic Import` panel and choose one of these approaches.

### Import A STDIO MCP Server

Example:

- `Transport`: `stdio`
- `Command`: `npx`
- `Args JSON`: `["-y","@amap/amap-maps-mcp-server"]`
- `Env JSON`: `{"AMAP_MAPS_API_KEY":"your_amap_key"}`

### Import A Remote HTTP MCP Server

Example:

- `Transport`: `http`
- `URL`: `https://your-mcp-server.example.com/mcp`
- `Headers JSON`: `{"Authorization":"your_token","Content-Type":"application/json"}`

After import:

1. refresh metadata
2. confirm the server is `HEALTHY` or `DEGRADED`
3. select one or more imported tools
4. send a prompt in `Agent Chat / ReAct` mode

## 6. Optional LLM Configuration

Set one supported key if you want real external model planning:

- `ZAI_API_KEY`
- `GLM_API_KEY`
- `LLM_API_KEY`
- `OPENAI_API_KEY`

Example in PowerShell:

```powershell
$env:ZAI_API_KEY = "your_key"
$env:LLM_MODEL = "glm-5.1"
```

Without an LLM key, the app still runs and the Studio remains demoable, but agent behavior falls back to local heuristics.

## 7. Public Demo Truthfulness

The public GitHub edition intentionally does **not** preload live secrets or local MCP runtimes.

That means:

- the app starts safely out of the box
- workflow mode always works
- real MCP and real LLM demos require your own configuration

This is intentional and makes the public repository safer and easier to trust.
