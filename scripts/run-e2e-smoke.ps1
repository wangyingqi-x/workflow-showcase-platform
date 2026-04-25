param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$Profile = "demo",
    [string]$JarPath = "workflow-web/target/workflow-web-1.0.0-SNAPSHOT.jar",
    [bool]$StartApp = $true,
    [switch]$BuildJar,
    [string]$ArtifactsDir = "temp/smoke-e2e",
    [string]$ExternalMcpName = $env:SMOKE_EXTERNAL_MCP_NAME,
    [string]$ExternalMcpTransport = $env:SMOKE_EXTERNAL_MCP_TRANSPORT,
    [string]$ExternalMcpUrl = $env:SMOKE_EXTERNAL_MCP_URL,
    [string]$ExternalMcpCommand = $env:SMOKE_EXTERNAL_MCP_COMMAND,
    [string]$ExternalMcpArgsJson = $env:SMOKE_EXTERNAL_MCP_ARGS_JSON,
    [string]$ExternalMcpEnvJson = $env:SMOKE_EXTERNAL_MCP_ENV_JSON,
    [string]$ExternalMcpAuthToken = $env:SMOKE_EXTERNAL_MCP_AUTH_TOKEN,
    [string]$ExternalMcpProtocolVersion = $(if ([string]::IsNullOrWhiteSpace($env:SMOKE_EXTERNAL_MCP_PROTOCOL_VERSION)) { "2025-03-26" } else { $env:SMOKE_EXTERNAL_MCP_PROTOCOL_VERSION }),
    [string]$ExternalMcpHeadersJson = $env:SMOKE_EXTERNAL_MCP_HEADERS_JSON,
    [string]$SmokeToolName = $env:SMOKE_TOOL_NAME,
    [string]$SmokeToolArgsJson = $env:SMOKE_TOOL_ARGS_JSON,
    [string]$SmokeUserMessage = $(if ([string]::IsNullOrWhiteSpace($env:SMOKE_USER_MESSAGE)) { "Please call the configured tool once and summarize the result." } else { $env:SMOKE_USER_MESSAGE }),
    [string]$LlmProvider = $(if ([string]::IsNullOrWhiteSpace($env:SMOKE_LLM_PROVIDER)) { $env:LLM_PROVIDER } else { $env:SMOKE_LLM_PROVIDER }),
    [string]$LlmModel = $(if ([string]::IsNullOrWhiteSpace($env:SMOKE_LLM_MODEL)) { $env:LLM_MODEL } else { $env:SMOKE_LLM_MODEL }),
    [string]$LlmApiKey = $(if (-not [string]::IsNullOrWhiteSpace($env:SMOKE_LLM_API_KEY)) { $env:SMOKE_LLM_API_KEY } elseif (-not [string]::IsNullOrWhiteSpace($env:ZAI_API_KEY)) { $env:ZAI_API_KEY } elseif (-not [string]::IsNullOrWhiteSpace($env:GLM_API_KEY)) { $env:GLM_API_KEY } elseif (-not [string]::IsNullOrWhiteSpace($env:LLM_API_KEY)) { $env:LLM_API_KEY } else { $env:OPENAI_API_KEY }),
    [switch]$RequireExternalMcp,
    [switch]$RequireExternalLlm
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Section([string]$Message) {
    Write-Host ""
    Write-Host "== $Message ==" -ForegroundColor Cyan
}

function Save-Json([string]$Path, $Value) {
    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $Value | ConvertTo-Json -Depth 100 | Set-Content -Path $Path -Encoding UTF8
}

function Read-JsonObject([string]$JsonText, $DefaultValue) {
    if ([string]::IsNullOrWhiteSpace($JsonText)) {
        return $DefaultValue
    }
    return $JsonText | ConvertFrom-Json
}

function Invoke-JsonApi([string]$Method, [string]$Url, $Body = $null) {
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -TimeoutSec 60
    }
    return Invoke-RestMethod -Method $Method -Uri $Url -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 100) -TimeoutSec 120
}

function Wait-HttpReady([string]$Url, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            return Invoke-JsonApi -Method GET -Url $Url
        } catch {
            Start-Sleep -Milliseconds 1500
        }
    }
    throw "Timed out waiting for service readiness: $Url"
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Get-ObjectPropertyValue($Object, [string]$PropertyName) {
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Get-AgentDecisionSource($Response, [string]$NodeId) {
    $nodeVariables = Get-ObjectPropertyValue -Object $Response.variables -PropertyName $NodeId
    if ($null -eq $nodeVariables) {
        return $null
    }
    return Get-ObjectPropertyValue -Object $nodeVariables -PropertyName "decision_source"
}

function Get-ToolResult($Response, [string]$QualifiedToolName) {
    return Get-ObjectPropertyValue -Object $Response.toolResults -PropertyName $QualifiedToolName
}

function Get-QualifiedToolName([string]$ServerName, [string]$ToolName) {
    return "$ServerName`__$ToolName"
}

function Resolve-ExternalMcpTransport() {
    if (-not [string]::IsNullOrWhiteSpace($ExternalMcpTransport)) {
        return $ExternalMcpTransport.Trim().ToLowerInvariant()
    }
    if (-not [string]::IsNullOrWhiteSpace($ExternalMcpCommand)) {
        return "stdio"
    }
    return "http"
}

function Resolve-QualifiedImportedToolName([string]$ServerName, [string[]]$ImportedTools, [string]$ConfiguredToolName) {
    if ($ImportedTools.Count -eq 0) {
        throw "No imported tools were returned."
    }

    if ([string]::IsNullOrWhiteSpace($ConfiguredToolName)) {
        return [string]$ImportedTools[0]
    }

    if ($ImportedTools -contains $ConfiguredToolName) {
        return $ConfiguredToolName
    }

    $qualifiedName = Get-QualifiedToolName -ServerName $ServerName -ToolName $ConfiguredToolName
    if ($ImportedTools -contains $qualifiedName) {
        return $qualifiedName
    }

    throw "Configured SMOKE_TOOL_NAME '$ConfiguredToolName' was not found. Imported tools: $($ImportedTools -join ', ')"
}

function Get-RawToolName([string]$ServerName, [string]$QualifiedToolName) {
    $prefix = "$ServerName`__"
    if ($QualifiedToolName.StartsWith($prefix)) {
        return $QualifiedToolName.Substring($prefix.Length)
    }
    return $QualifiedToolName
}

function New-BaselineWorkflowPayload() {
    return @{
        workflowId = "smoke-baseline-workflow"
        workflowName = "Smoke Baseline Workflow"
        description = "Nodes-only baseline workflow for public smoke verification"
        userMessage = "I want a relaxed Nanjing itinerary and rainy weather is acceptable."
        asyncMode = $true
        nodes = @(
            @{
                id = "start-1"
                type = "start"
                name = "Start"
                guideWord = "I want a relaxed Nanjing itinerary and rainy weather is acceptable."
                guideQuestions = @()
                params = @{}
            },
            @{
                id = "condition-pace-1"
                type = "condition"
                name = "Pace Router"
                params = @{
                    default_target = "output-pace-balanced-1"
                    conditions = @(
                        @{
                            name = "pace_relaxed"
                            source = "start-1.guide_word"
                            operator = "contains"
                            value = "relaxed"
                            target = "output-pace-relaxed-1"
                        }
                    )
                }
            },
            @{
                id = "condition-weather-1"
                type = "condition"
                name = "Weather Router"
                params = @{
                    default_target = "output-weather-standard-1"
                    conditions = @(
                        @{
                            name = "rain_recovery"
                            source = "start-1.guide_word"
                            operator = "contains"
                            value = "rain"
                            target = "output-weather-rain-1"
                        }
                    )
                }
            },
            @{
                id = "output-pace-relaxed-1"
                type = "output"
                name = "Relaxed Pace"
                params = @{
                    message = "Pace branch: keep the itinerary light and avoid dense transfers."
                }
            },
            @{
                id = "output-pace-balanced-1"
                type = "output"
                name = "Balanced Pace"
                params = @{
                    message = "Pace branch: use a balanced itinerary."
                }
            },
            @{
                id = "output-weather-rain-1"
                type = "output"
                name = "Rain Branch"
                params = @{
                    message = "Weather branch: prepare indoor alternatives and umbrella time."
                }
            },
            @{
                id = "output-weather-standard-1"
                type = "output"
                name = "Standard Weather"
                params = @{
                    message = "Weather branch: use the standard mixed itinerary."
                }
            },
            @{
                id = "runwait-plan-1"
                type = "runwait"
                name = "Join"
                params = @{}
            },
            @{
                id = "output-summary-1"
                type = "output"
                name = "Summary"
                params = @{
                    message = "Baseline workflow summary.`nPace: {{#condition-pace-1.matched_rule#}}`nWeather: {{#condition-weather-1.matched_rule#}}`n{{#output-pace-relaxed-1.message#}}{{#output-pace-balanced-1.message#}}`n{{#output-weather-rain-1.message#}}{{#output-weather-standard-1.message#}}"
                }
            },
            @{
                id = "end-1"
                type = "end"
                name = "End"
                params = @{
                    output_variable = @(
                        @{
                            key = "final_answer"
                            value = "output-summary-1.message"
                            type = "ref"
                        },
                        @{
                            key = "pace_branch"
                            value = "condition-pace-1.matched_rule"
                            type = "ref"
                        },
                        @{
                            key = "weather_branch"
                            value = "condition-weather-1.matched_rule"
                            type = "ref"
                        }
                    )
                }
            }
        )
        edges = @(
            @{ source = "start-1"; target = "condition-pace-1"; sourceHandle = "pace" }
            @{ source = "start-1"; target = "condition-weather-1"; sourceHandle = "weather" }
            @{ source = "condition-pace-1"; target = "output-pace-relaxed-1"; sourceHandle = "branch" }
            @{ source = "condition-pace-1"; target = "output-pace-balanced-1"; sourceHandle = "branch" }
            @{ source = "condition-weather-1"; target = "output-weather-rain-1"; sourceHandle = "branch" }
            @{ source = "condition-weather-1"; target = "output-weather-standard-1"; sourceHandle = "branch" }
            @{ source = "output-pace-relaxed-1"; target = "runwait-plan-1"; sourceHandle = "done" }
            @{ source = "output-pace-balanced-1"; target = "runwait-plan-1"; sourceHandle = "done" }
            @{ source = "output-weather-rain-1"; target = "runwait-plan-1"; sourceHandle = "done" }
            @{ source = "output-weather-standard-1"; target = "runwait-plan-1"; sourceHandle = "done" }
            @{ source = "runwait-plan-1"; target = "output-summary-1"; sourceHandle = "joined" }
            @{ source = "output-summary-1"; target = "end-1"; sourceHandle = "final" }
        )
    }
}

function New-ExternalTransportPayload([string]$QualifiedToolName, $ToolArgs) {
    return @{
        workflowId = "smoke-external-mcp-transport"
        workflowName = "Smoke External MCP Transport"
        description = "Transport-only verification for an imported MCP tool"
        userMessage = "Run the configured external MCP tool."
        asyncMode = $true
        nodes = @(
            @{
                id = "start-1"
                type = "start"
                name = "Start"
                guideWord = "Execute the configured external MCP tool."
                guideQuestions = @()
                params = @{}
            },
            @{
                id = "tool-1"
                type = "tool"
                name = "External MCP Tool"
                params = @{
                    tool_name = $QualifiedToolName
                    tool_args = $ToolArgs
                }
            },
            @{
                id = "end-1"
                type = "end"
                name = "End"
                params = @{}
            }
        )
        edges = @(
            @{ source = "start-1"; target = "tool-1"; sourceHandle = "main" }
            @{ source = "tool-1"; target = "end-1"; sourceHandle = "done" }
        )
    }
}

function New-ExternalAgentPayload([string]$QualifiedToolName, $ToolArgs, [string]$UserMessage) {
    return @{
        workflowId = "smoke-external-agent-e2e"
        workflowName = "Smoke External Agent E2E"
        description = "External MCP plus external LLM orchestration smoke flow"
        userMessage = $UserMessage
        asyncMode = $true
        nodes = @(
            @{
                id = "start-1"
                type = "start"
                name = "Start"
                guideWord = $UserMessage
                guideQuestions = @("Should the planner call the configured tool?")
                params = @{}
            },
            @{
                id = "agent-plan-1"
                type = "agent"
                name = "Planner"
                params = @{
                    agent_stage = "plan"
                    system_prompt = "You are validating external LLM and external MCP integration. You must call $QualifiedToolName exactly once, then stop planning and let the summarizer answer."
                    assistant_message = "Planning the external MCP call."
                    react_loop_enabled = $true
                    react_max_iterations = 2
                    final_target = "agent-summarize-1"
                    tool_names = @($QualifiedToolName)
                    tool_target_map = @{
                        $QualifiedToolName = "tool-1"
                    }
                    tool_args = $ToolArgs
                    user_message_ref = "start-1.guide_word"
                }
            },
            @{
                id = "tool-1"
                type = "tool"
                name = "External MCP Tool"
                params = @{
                    tool_name = $QualifiedToolName
                    tool_args = @{}
                }
            },
            @{
                id = "agent-summarize-1"
                type = "agent"
                name = "Summarizer"
                params = @{
                    agent_stage = "synthesize"
                    system_prompt = "Summarize the observed external MCP tool result clearly."
                    assistant_message = "Summarizing the external MCP result."
                    user_message_ref = "start-1.guide_word"
                }
            },
            @{
                id = "end-1"
                type = "end"
                name = "End"
                params = @{}
            }
        )
        edges = @(
            @{ source = "start-1"; target = "agent-plan-1"; sourceHandle = "main" }
            @{ source = "agent-plan-1"; target = "tool-1"; sourceHandle = "tool" }
            @{ source = "agent-plan-1"; target = "agent-summarize-1"; sourceHandle = "final" }
            @{ source = "tool-1"; target = "agent-plan-1"; sourceHandle = "after_tool" }
            @{ source = "agent-summarize-1"; target = "end-1"; sourceHandle = "final" }
        )
    }
}

$workspaceRoot = (Resolve-Path ".").Path
$artifactsRoot = Join-Path $workspaceRoot $ArtifactsDir
New-Item -ItemType Directory -Force -Path $artifactsRoot | Out-Null

$jarAbsolutePath = Join-Path $workspaceRoot $JarPath
if ($BuildJar) {
    Write-Section "Building Jar"
    & mvn -q -DskipTests package
}
Assert-True (Test-Path $jarAbsolutePath) "Jar not found: $jarAbsolutePath"

$baseUri = [Uri]$BaseUrl
$serverPort = if ($baseUri.IsDefaultPort) { 80 } else { $baseUri.Port }
$stdoutLog = Join-Path $artifactsRoot "app.out.log"
$stderrLog = Join-Path $artifactsRoot "app.err.log"
$appProcess = $null

try {
    if ($StartApp) {
        Write-Section "Starting App"
        $javaArgs = @(
            "-jar",
            $jarAbsolutePath,
            "--spring.profiles.active=$Profile",
            "--server.port=$serverPort"
        )
        if (-not [string]::IsNullOrWhiteSpace($LlmProvider)) {
            $javaArgs += "--llm.provider=$LlmProvider"
        }
        if (-not [string]::IsNullOrWhiteSpace($LlmModel)) {
            $javaArgs += "--llm.model=$LlmModel"
        }
        if (-not [string]::IsNullOrWhiteSpace($LlmApiKey)) {
            $javaArgs += "--llm.api-key=$LlmApiKey"
        }
        $appProcess = Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $workspaceRoot -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    } else {
        Write-Section "Using Existing App"
    }

    Write-Section "Loading Metadata"
    $metadata = Wait-HttpReady -Url "$BaseUrl/api/demo/workflows/studio-metadata" -TimeoutSeconds 90
    Save-Json -Path (Join-Path $artifactsRoot "studio-metadata.json") -Value $metadata

    $summary = [ordered]@{
        baseUrl = $BaseUrl
        llmConfigured = [bool]$metadata.llm.configured
        baseline = [ordered]@{ status = "PENDING" }
        externalMcpTransport = [ordered]@{ status = "SKIPPED" }
        externalAgentE2E = [ordered]@{ status = "SKIPPED" }
    }

    Write-Section "Running Baseline Workflow"
    $baselinePayload = New-BaselineWorkflowPayload
    Save-Json -Path (Join-Path $artifactsRoot "baseline-request.json") -Value $baselinePayload
    $baselineResponse = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/demo/workflows/run" -Body $baselinePayload
    Save-Json -Path (Join-Path $artifactsRoot "baseline-response.json") -Value $baselineResponse
    Assert-True ($baselineResponse.status -eq "COMPLETED") "Baseline workflow did not complete successfully."
    Assert-True (-not [string]::IsNullOrWhiteSpace($baselineResponse.assistantReply)) "Baseline workflow did not produce a final answer."
    $summary.baseline = [ordered]@{
        status = "PASSED"
        assistantReply = $baselineResponse.assistantReply
        paceBranch = Get-ObjectPropertyValue -Object $baselineResponse.variables.'end-1' -PropertyName "pace_branch"
        weatherBranch = Get-ObjectPropertyValue -Object $baselineResponse.variables.'end-1' -PropertyName "weather_branch"
    }

    $externalMcpTransport = Resolve-ExternalMcpTransport
    $hasExternalMcpConfig = if ($externalMcpTransport -eq "stdio") {
        -not [string]::IsNullOrWhiteSpace($ExternalMcpCommand)
    } else {
        -not [string]::IsNullOrWhiteSpace($ExternalMcpUrl)
    }

    if (-not $hasExternalMcpConfig) {
        if ($RequireExternalMcp) {
            if ($externalMcpTransport -eq "stdio") {
                throw "External MCP is required but SMOKE_EXTERNAL_MCP_COMMAND was not provided."
            }
            throw "External MCP is required but SMOKE_EXTERNAL_MCP_URL was not provided."
        }
        Write-Section "Skipping External MCP"
    } else {
        Write-Section "Importing External MCP"
        $importBody = @{
            name = $(if ([string]::IsNullOrWhiteSpace($ExternalMcpName)) { "external-smoke-mcp" } else { $ExternalMcpName })
            transport = $externalMcpTransport
            url = $ExternalMcpUrl
            command = $ExternalMcpCommand
            args = @((Read-JsonObject -JsonText $ExternalMcpArgsJson -DefaultValue @()))
            env = Read-JsonObject -JsonText $ExternalMcpEnvJson -DefaultValue @{}
            authToken = $ExternalMcpAuthToken
            protocolVersion = $ExternalMcpProtocolVersion
            enabled = $true
            headers = Read-JsonObject -JsonText $ExternalMcpHeadersJson -DefaultValue @{}
            tools = @()
        }
        Save-Json -Path (Join-Path $artifactsRoot "external-mcp-import-request.json") -Value $importBody
        $importResponse = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/demo/mcp/registry/import" -Body $importBody
        Save-Json -Path (Join-Path $artifactsRoot "external-mcp-import-response.json") -Value $importResponse

        $serverStatus = $importResponse.server
        Assert-True ($null -ne $serverStatus) "External MCP import did not return server status."
        $serverName = [string]$serverStatus.name
        $importedTools = @($serverStatus.importedTools)
        Assert-True ($importedTools.Count -gt 0) "External MCP import succeeded but no tools were discovered."

        $qualifiedToolName = Resolve-QualifiedImportedToolName -ServerName $serverName -ImportedTools $importedTools -ConfiguredToolName $SmokeToolName
        $selectedTool = Get-RawToolName -ServerName $serverName -QualifiedToolName $qualifiedToolName
        $toolArgs = Read-JsonObject -JsonText $SmokeToolArgsJson -DefaultValue @{}

        Write-Section "Running External MCP Transport Check"
        $transportPayload = New-ExternalTransportPayload -QualifiedToolName $qualifiedToolName -ToolArgs $toolArgs
        Save-Json -Path (Join-Path $artifactsRoot "external-transport-request.json") -Value $transportPayload
        $transportResponse = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/demo/workflows/run" -Body $transportPayload
        Save-Json -Path (Join-Path $artifactsRoot "external-transport-response.json") -Value $transportResponse
        Assert-True ($transportResponse.status -eq "COMPLETED") "External MCP transport workflow did not complete successfully."
        Assert-True ($null -ne (Get-ToolResult -Response $transportResponse -QualifiedToolName $qualifiedToolName)) "External MCP transport workflow did not produce the expected tool result."
        $summary.externalMcpTransport = [ordered]@{
            status = "PASSED"
            serverName = $serverName
            transport = $externalMcpTransport
            toolName = $selectedTool
            qualifiedToolName = $qualifiedToolName
        }

        if (-not [bool]$metadata.llm.configured) {
            if ($RequireExternalLlm) {
                throw "External LLM is required but the app metadata reports llm.configured=false."
            }
            Write-Section "Skipping External LLM"
        } else {
            Write-Section "Running External Agent E2E Workflow"
            $agentPayload = New-ExternalAgentPayload -QualifiedToolName $qualifiedToolName -ToolArgs $toolArgs -UserMessage $SmokeUserMessage
            Save-Json -Path (Join-Path $artifactsRoot "external-agent-request.json") -Value $agentPayload
            $agentResponse = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/demo/workflows/run" -Body $agentPayload
            Save-Json -Path (Join-Path $artifactsRoot "external-agent-response.json") -Value $agentResponse
            Assert-True ($agentResponse.status -eq "COMPLETED") "External agent workflow did not complete successfully."
            Assert-True ($null -ne (Get-ToolResult -Response $agentResponse -QualifiedToolName $qualifiedToolName)) "External agent workflow did not produce the expected external MCP tool result."

            $plannerDecisionSource = [string](Get-AgentDecisionSource -Response $agentResponse -NodeId "agent-plan-1")
            Assert-True ($plannerDecisionSource -eq "REMOTE_LLM_PLAN") "Planner did not report REMOTE_LLM_PLAN. Actual decision source: '$plannerDecisionSource'"

            $summary.externalAgentE2E = [ordered]@{
                status = "PASSED"
                plannerDecisionSource = $plannerDecisionSource
                assistantReply = $agentResponse.assistantReply
                qualifiedToolName = $qualifiedToolName
            }
        }
    }

    Save-Json -Path (Join-Path $artifactsRoot "summary.json") -Value $summary
    Write-Section "Smoke Summary"
    $summary | ConvertTo-Json -Depth 20
} finally {
    if ($null -ne $appProcess -and -not $appProcess.HasExited) {
        Write-Section "Stopping App"
        Stop-Process -Id $appProcess.Id -Force
    }
}
