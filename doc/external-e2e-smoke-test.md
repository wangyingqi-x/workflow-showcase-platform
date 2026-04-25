# External E2E Smoke Test

This repository includes a reusable PowerShell smoke script:

- [`scripts/run-e2e-smoke.ps1`](../scripts/run-e2e-smoke.ps1)

It validates the public demo in three layers:

1. baseline app startup plus a nodes-only workflow
2. external MCP import, discovery, and tool transport
3. external LLM plus external MCP orchestration through the Agent workflow

## What The Script Produces

Artifacts are written to `temp/smoke-e2e/` by default:

- `studio-metadata.json`
- `baseline-request.json`
- `baseline-response.json`
- `external-mcp-import-request.json`
- `external-mcp-import-response.json`
- `external-transport-request.json`
- `external-transport-response.json`
- `external-agent-request.json`
- `external-agent-response.json`
- `summary.json`
- `app.out.log`
- `app.err.log`

## Baseline Run

The baseline run is public-safe and does not require any external MCP server.

It verifies a nodes-only workflow with:

- dual conditional branches
- `RunWait` join behavior
- final output aggregation

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-smoke.ps1
```

The baseline phase should pass even when no real LLM key is configured.

## External MCP Run

Use this when you want to validate dynamic MCP import and tool execution.

### HTTP MCP

Provide at least one external MCP server URL:

```powershell
$env:SMOKE_EXTERNAL_MCP_URL = "https://your-mcp-server.example.com/mcp"
$env:SMOKE_EXTERNAL_MCP_NAME = "remote-mcp"
$env:SMOKE_TOOL_NAME = "your_tool_name"
$env:SMOKE_TOOL_ARGS_JSON = '{"message":"hello from smoke test"}'

powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-smoke.ps1 -RequireExternalMcp
```

Optional environment variables:

- `SMOKE_EXTERNAL_MCP_AUTH_TOKEN`
- `SMOKE_EXTERNAL_MCP_PROTOCOL_VERSION`
- `SMOKE_EXTERNAL_MCP_HEADERS_JSON`

If the selected tool has required fields, `SMOKE_TOOL_ARGS_JSON` must include them.

### STDIO MCP

The script also supports command-based MCP servers:

```powershell
$env:SMOKE_EXTERNAL_MCP_TRANSPORT = "stdio"
$env:SMOKE_EXTERNAL_MCP_NAME = "amap-mcp"
$env:SMOKE_EXTERNAL_MCP_COMMAND = "npx"
$env:SMOKE_EXTERNAL_MCP_ARGS_JSON = '["-y","@amap/amap-maps-mcp-server"]'
$env:SMOKE_EXTERNAL_MCP_ENV_JSON = '{"AMAP_MAPS_API_KEY":"your_amap_key"}'
$env:SMOKE_TOOL_NAME = "maps_geo"
$env:SMOKE_TOOL_ARGS_JSON = '{"address":"Nanjing South Railway Station"}'

powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-smoke.ps1 -RequireExternalMcp
```

Optional environment variables for stdio MCP:

- `SMOKE_EXTERNAL_MCP_COMMAND`
- `SMOKE_EXTERNAL_MCP_ARGS_JSON`
- `SMOKE_EXTERNAL_MCP_ENV_JSON`

If `SMOKE_TOOL_NAME` is omitted, the script automatically uses the first imported runtime tool.

## External LLM Plus External MCP Run

Set one supported LLM key before running:

- `ZAI_API_KEY`
- `GLM_API_KEY`
- `LLM_API_KEY`
- `OPENAI_API_KEY`

Example:

```powershell
$env:SMOKE_LLM_PROVIDER = "GLM"
$env:SMOKE_LLM_MODEL = "glm-5.1"
$env:SMOKE_LLM_API_KEY = "your_llm_key"
$env:SMOKE_EXTERNAL_MCP_TRANSPORT = "stdio"
$env:SMOKE_EXTERNAL_MCP_NAME = "amap-mcp"
$env:SMOKE_EXTERNAL_MCP_COMMAND = "npx"
$env:SMOKE_EXTERNAL_MCP_ARGS_JSON = '["-y","@amap/amap-maps-mcp-server"]'
$env:SMOKE_EXTERNAL_MCP_ENV_JSON = '{"AMAP_MAPS_API_KEY":"your_amap_key"}'
$env:SMOKE_TOOL_NAME = "maps_geo"
$env:SMOKE_TOOL_ARGS_JSON = '{"address":"Nanjing South Railway Station"}'
$env:SMOKE_USER_MESSAGE = "Please call the configured tool once and summarize the result."

powershell -ExecutionPolicy Bypass -File .\scripts\run-e2e-smoke.ps1 -RequireExternalMcp -RequireExternalLlm
```

The script checks `agent-plan-1.decision_source` and expects:

```text
REMOTE_LLM_PLAN
```

That means the planner actually used the configured external LLM instead of silently falling back to local heuristic planning.

## Notes

- The script starts the Spring Boot jar on `http://127.0.0.1:18080` by default.
- Use `-BaseUrl` if you need another port.
- Use `-BuildJar` if you want the script to package the app before starting it.
- If your app is already running, pass `-StartApp:$false`.
- When the script starts the app itself, it forwards `--llm.provider`, `--llm.model`, and `--llm.api-key` so the Java process sees the same LLM configuration.
- Imported MCP tool names are runtime-qualified as `server__tool`.
- The public repository does not bundle MCP runtimes or keys. Install those yourself before running the external phases.
