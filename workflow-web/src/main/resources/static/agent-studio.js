(function () {
    const MAX_TIMELINE_ITEMS = 80;
    const MAX_TIMELINE_PREVIEW_CHARS = 360;

    const MCP_IMPORT_EXAMPLES = {
        amap: {
            name: "amap-mcp",
            transport: "stdio",
            command: "npx",
            args: ["-y", "@amap/amap-maps-mcp-server"],
            env: {
                AMAP_MAPS_API_KEY: "your_amap_key"
            },
            url: "",
            authToken: "",
            headers: {}
        },
        train12306: {
            name: "train-12306-mcp",
            transport: "stdio",
            command: "npx",
            args: ["-y", "12306-mcp"],
            env: {},
            url: "",
            authToken: "",
            headers: {}
        }
    };

    const state = {
        metadata: null,
        mcpServers: [],
        mcpTools: [],
        selectedTools: new Set(),
        workflowMode: "auto",
        conversation: [],
        loading: false,
        currentRunRequest: null,
        runResult: null,
        interactionSubmitting: "",
        importStatus: "Import a stdio or HTTP MCP server and the discovered tools will appear below.",
        streaming: {
            assistantText: "",
            agentStages: {},
            timeline: [],
            trace: [],
            toolCalls: [],
            toolResults: [],
            interactionContext: null,
            logs: 0
        },
        requestPreviewCacheKey: "",
        requestPreviewCacheText: "",
        renderQueued: false
    };

    const elements = {
        refreshMetadata: document.getElementById("refresh-metadata"),
        refreshMcp: document.getElementById("refresh-mcp"),
        selectAllTools: document.getElementById("select-all-tools"),
        clearTools: document.getElementById("clear-tools"),
        serverList: document.getElementById("server-list"),
        toolGroups: document.getElementById("tool-groups"),
        selectedToolsPill: document.getElementById("selected-tools-pill"),
        chatHistory: document.getElementById("chat-history"),
        chatInteractionSlot: document.getElementById("chat-interaction-slot"),
        workflowMode: document.getElementById("workflow-mode"),
        workflowModeNote: document.getElementById("workflow-mode-note"),
        chatInput: document.getElementById("chat-input"),
        sendChat: document.getElementById("send-chat"),
        clearChat: document.getElementById("clear-chat"),
        interactionPanel: document.getElementById("interaction-panel"),
        interactionContent: document.getElementById("interaction-content"),
        requestPreview: document.getElementById("request-preview"),
        runStatus: document.getElementById("run-status"),
        summaryList: document.getElementById("summary-list"),
        traceList: document.getElementById("trace-list"),
        toolList: document.getElementById("tool-list"),
        timeline: document.getElementById("timeline"),
        variablesView: document.getElementById("variables-view"),
        metricSteps: document.getElementById("metric-steps"),
        metricLogs: document.getElementById("metric-logs"),
        metricCheckpoint: document.getElementById("metric-checkpoint"),
        heroLlm: document.getElementById("hero-llm"),
        heroLlmNote: document.getElementById("hero-llm-note"),
        heroServerCount: document.getElementById("hero-server-count"),
        heroToolCount: document.getElementById("hero-tool-count"),
        heroSelectedCount: document.getElementById("hero-selected-count"),
        importName: document.getElementById("import-name"),
        importTransport: document.getElementById("import-transport"),
        importCommand: document.getElementById("import-command"),
        importArgs: document.getElementById("import-args"),
        importEnv: document.getElementById("import-env"),
        importUrl: document.getElementById("import-url"),
        importAuthToken: document.getElementById("import-auth-token"),
        importHeaders: document.getElementById("import-headers"),
        importMcp: document.getElementById("import-mcp"),
        fillAmapImport: document.getElementById("fill-amap-import"),
        fill12306Import: document.getElementById("fill-12306-import"),
        importStatus: document.getElementById("import-status")
    };

    initialize().catch(error => {
        console.error(error);
        setRunStatus("Failed to initialize Studio", "failed");
        renderErrorState("Failed to initialize Agent Studio: " + readErrorMessage(error));
    });

    async function initialize() {
        bindEvents();
        setRunStatus("Loading metadata...", "running");
        render();
        await reloadMetadata({ preserveSelection: false, autoSelectHealthy: false });
        updateRequestPreview(elements.chatInput.value.trim());
        setRunStatus("Ready", "");
    }

    function bindEvents() {
        elements.refreshMetadata.addEventListener("click", () => reloadMetadata({
            preserveSelection: true,
            autoSelectHealthy: false
        }));
        elements.refreshMcp.addEventListener("click", refreshAllMcpServers);
        elements.selectAllTools.addEventListener("click", () => {
            state.selectedTools = new Set(eligibleToolNames());
            updateRequestPreview(elements.chatInput.value.trim());
            render();
        });
        elements.clearTools.addEventListener("click", () => {
            state.selectedTools = new Set();
            updateRequestPreview(elements.chatInput.value.trim());
            render();
        });
        elements.toolGroups.addEventListener("change", onToolSelectionChanged);
        elements.toolGroups.addEventListener("click", onToolGroupsClicked);
        elements.serverList.addEventListener("click", onServerListClicked);
        elements.workflowMode.addEventListener("change", onWorkflowModeChanged);
        elements.chatInput.addEventListener("input", () => updateRequestPreview(elements.chatInput.value.trim()));
        elements.chatInput.addEventListener("keydown", event => {
            if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
                event.preventDefault();
                submitChat();
            }
        });
        elements.sendChat.addEventListener("click", submitChat);
        elements.clearChat.addEventListener("click", clearConversation);
        elements.interactionPanel.addEventListener("click", onInteractionPanelClicked);
        if (elements.chatInteractionSlot) {
            elements.chatInteractionSlot.addEventListener("click", onInteractionPanelClicked);
        }
        elements.importMcp.addEventListener("click", importMcpServer);
        elements.fillAmapImport.addEventListener("click", () => fillImportForm(MCP_IMPORT_EXAMPLES.amap));
        elements.fill12306Import.addEventListener("click", () => fillImportForm(MCP_IMPORT_EXAMPLES.train12306));
        elements.importTransport.addEventListener("change", renderImportStatus);
    }

    function onWorkflowModeChanged() {
        state.workflowMode = String(elements.workflowMode.value || "auto");
        updateComposerForMode();
        updateRequestPreview(elements.chatInput.value.trim());
        render();
    }

    async function reloadMetadata(options) {
        const settings = Object.assign({
            preserveSelection: true,
            autoSelectHealthy: false
        }, options || {});

        const metadata = await requestJson("/api/demo/workflows/studio-metadata");
        state.metadata = metadata || {};
        state.mcpServers = Array.isArray(state.metadata?.mcp?.servers) ? state.metadata.mcp.servers.slice() : [];
        state.mcpTools = (Array.isArray(state.metadata?.tools) ? state.metadata.tools : [])
            .filter(tool => tool && tool.type === "mcp")
            .sort((left, right) => String(left.name).localeCompare(String(right.name)));

        syncSelectedTools(settings);
        updateRequestPreview(elements.chatInput.value.trim());
        render();
    }

    function syncSelectedTools(options) {
        const availableNames = new Set(state.mcpTools.map(tool => tool.name));
        let nextSelection = new Set();

        if (options.preserveSelection) {
            for (const toolName of state.selectedTools) {
                if (availableNames.has(toolName)) {
                    nextSelection.add(toolName);
                }
            }
        }

        if (nextSelection.size === 0 && options.autoSelectHealthy) {
            for (const toolName of eligibleToolNames()) {
                nextSelection.add(toolName);
            }
        }

        state.selectedTools = nextSelection;
    }

    function eligibleToolNames() {
        const healthyServers = new Set(
            state.mcpServers
                .filter(server => {
                    const status = String(server?.status || "").toUpperCase();
                    return status === "HEALTHY" || status === "DEGRADED";
                })
                .map(server => server.name)
        );

        return state.mcpTools
            .filter(tool => healthyServers.has(tool.serverName))
            .map(tool => tool.name);
    }

    function onToolSelectionChanged(event) {
        const checkbox = event.target;
        if (!(checkbox instanceof HTMLInputElement) || checkbox.type !== "checkbox") {
            return;
        }
        const toolName = checkbox.dataset.toolName;
        if (!toolName) {
            return;
        }
        if (checkbox.checked) {
            state.selectedTools.add(toolName);
        } else {
            state.selectedTools.delete(toolName);
        }
        updateRequestPreview(elements.chatInput.value.trim());
        render();
    }

    function onToolGroupsClicked(event) {
        const button = event.target.closest("[data-schema-toggle]");
        if (!button) {
            return;
        }
        const targetId = button.getAttribute("data-schema-toggle");
        if (!targetId) {
            return;
        }
        const panel = document.getElementById(targetId);
        if (!panel) {
            return;
        }
        const expanded = button.getAttribute("aria-expanded") === "true";
        if (expanded) {
            panel.hidden = true;
            button.setAttribute("aria-expanded", "false");
            button.textContent = "View schema";
            return;
        }

        if (!panel.dataset.loaded) {
            panel.textContent = panel.dataset.schemaText || "{}";
            panel.dataset.loaded = "true";
        }
        panel.hidden = false;
        button.setAttribute("aria-expanded", "true");
        button.textContent = "Hide schema";
    }

    async function onServerListClicked(event) {
        const button = event.target.closest("[data-server-refresh]");
        if (!button) {
            return;
        }
        const serverName = button.getAttribute("data-server-refresh");
        if (!serverName) {
            return;
        }

        setRunStatus("Refreshing " + serverName + "...", "running");
        try {
            await requestJson("/api/demo/mcp/registry/refresh/" + encodeURIComponent(serverName), {
                method: "POST"
            });
            await reloadMetadata({ preserveSelection: true, autoSelectHealthy: false });
            setRunStatus("Refreshed " + serverName, "");
        } catch (error) {
            console.error(error);
            setRunStatus("Refresh failed", "failed");
        }
    }

    async function refreshAllMcpServers() {
        setRunStatus("Refreshing all MCP servers...", "running");
        try {
            await requestJson("/api/demo/mcp/registry/refresh", {
                method: "POST"
            });
            await reloadMetadata({ preserveSelection: true, autoSelectHealthy: false });
            setRunStatus("All MCP servers refreshed", "");
        } catch (error) {
            console.error(error);
            setRunStatus("Refresh failed", "failed");
        }
    }

    function fillImportForm(example) {
        elements.importName.value = example.name || "";
        elements.importTransport.value = example.transport || "stdio";
        elements.importCommand.value = example.command || "";
        elements.importArgs.value = formatJson(example.args || []);
        elements.importEnv.value = formatJson(example.env || {});
        elements.importUrl.value = example.url || "";
        elements.importAuthToken.value = example.authToken || "";
        elements.importHeaders.value = formatJson(example.headers || {});
        renderImportStatus();
    }

    async function importMcpServer() {
        try {
            const payload = buildImportPayload();
            state.importStatus = "Importing " + payload.name + "...";
            renderImportStatus();
            const response = await requestJson("/api/demo/mcp/registry/import", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });
            const importedTools = Array.isArray(response?.server?.importedTools)
                ? response.server.importedTools
                : [];
            await reloadMetadata({ preserveSelection: true, autoSelectHealthy: false });
            importedTools.forEach(toolName => state.selectedTools.add(toolName));
            state.importStatus = "Imported " + payload.name + " successfully.";
            setRunStatus("MCP import completed", "");
            render();
        } catch (error) {
            console.error(error);
            state.importStatus = "Import failed: " + readErrorMessage(error);
            setRunStatus("Import failed", "failed");
            renderImportStatus();
        }
    }

    function buildImportPayload() {
        const transport = String(elements.importTransport.value || "stdio").trim().toLowerCase();
        const payload = {
            name: String(elements.importName.value || "").trim(),
            transport: transport,
            command: String(elements.importCommand.value || "").trim(),
            args: parseJsonField(elements.importArgs.value, [], "args JSON must be a JSON array."),
            env: parseJsonField(elements.importEnv.value, {}, "env JSON must be a JSON object."),
            url: String(elements.importUrl.value || "").trim(),
            authToken: String(elements.importAuthToken.value || "").trim(),
            headers: parseJsonField(elements.importHeaders.value, {}, "headers JSON must be a JSON object."),
            enabled: true,
            protocolVersion: "2025-03-26",
            tools: []
        };

        if (!payload.name) {
            throw new Error("Server name is required.");
        }
        if (transport === "stdio" && !payload.command) {
            throw new Error("stdio MCP import requires a command.");
        }
        if (transport === "http" && !payload.url) {
            throw new Error("HTTP MCP import requires a URL.");
        }
        return payload;
    }

    function parseJsonField(rawValue, fallbackValue, errorMessage) {
        const text = String(rawValue || "").trim();
        if (!text) {
            return fallbackValue;
        }
        let parsed;
        try {
            parsed = JSON.parse(text);
        } catch (error) {
            throw new Error(errorMessage);
        }
        const wantsArray = Array.isArray(fallbackValue);
        if (wantsArray && !Array.isArray(parsed)) {
            throw new Error(errorMessage);
        }
        if (!wantsArray && (parsed === null || Array.isArray(parsed) || typeof parsed !== "object")) {
            throw new Error(errorMessage);
        }
        return parsed;
    }

    async function submitChat() {
        const userMessage = String(elements.chatInput.value || "").trim();
        if (!userMessage || state.loading) {
            return;
        }

        const nextConversation = state.conversation.concat([{
            role: "user",
            message: userMessage
        }]);
        const request = buildWorkflowRequest(userMessage, nextConversation);
        state.currentRunRequest = request;
        state.runResult = null;
        state.streaming = createStreamingState();
        state.loading = true;
        state.conversation = nextConversation;
        elements.chatInput.value = "";
        setRunStatus("Running workflow...", "running");
        updateRequestPreview("");
        render();

        try {
            await streamWorkflow(request);
            const assistantReply = readAssistantReply();
            if (assistantReply && !isWaitingResult()) {
                state.conversation = state.conversation.concat([{
                    role: "assistant",
                    message: assistantReply
                }]);
            }
            state.loading = false;
            state.streaming.assistantText = "";
            if (isWaitingResult()) {
                setRunStatus("Waiting for interaction", "waiting");
            } else {
                setRunStatus("Completed", "");
            }
            render();
        } catch (error) {
            console.error(error);
            state.loading = false;
            state.streaming.assistantText = "Request failed: " + readErrorMessage(error);
            setRunStatus("Run failed", "failed");
            render();
        }
    }

    function clearConversation() {
        state.conversation = [];
        state.runResult = null;
        state.currentRunRequest = null;
        state.streaming = createStreamingState();
        state.interactionSubmitting = "";
        state.loading = false;
        elements.chatInput.value = "";
        setRunStatus("Ready", "");
        updateRequestPreview("");
        render();
    }

    function onInteractionPanelClicked(event) {
        const button = event.target.closest("[data-resume-action]");
        if (!button) {
            return;
        }
        const action = String(button.getAttribute("data-resume-action") || "").trim();
        if (!action || state.loading) {
            return;
        }
        resumeInteraction(action);
    }

    async function resumeInteraction(action) {
        const interaction = currentInteractionContext();
        const checkpointId = String(state.runResult?.checkpointId || "").trim();
        if (!interaction || !checkpointId) {
            return;
        }

        const payload = buildInteractionPayload(interaction, action);
        const conversationMessage = interactionConversationMessage(interaction, payload);

        state.loading = true;
        state.interactionSubmitting = action;
        state.streaming.assistantText = "";
        if (conversationMessage) {
            state.conversation = state.conversation.concat([conversationMessage]);
        }
        setRunStatus("Resuming workflow...", "running");
        render();

        try {
            const response = await requestJson("/api/demo/workflows/resume", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    checkpointId: checkpointId,
                    interactionData: payload
                })
            });

            state.runResult = response;
            state.streaming.interactionContext = response?.interactionContext || null;
            state.streaming.timeline.push({
                event: "resume",
                payload: summarizeTimelinePayload("resume", {
                    checkpointId: checkpointId,
                    interactionData: payload,
                    status: response?.status || ""
                }),
                time: new Date().toISOString()
            });

            const assistantReply = readAssistantReply();
            if (assistantReply && !isWaitingResult()) {
                state.conversation = state.conversation.concat([{
                    role: "assistant",
                    message: assistantReply
                }]);
            }

            state.loading = false;
            state.interactionSubmitting = "";
            state.streaming.assistantText = "";
            if (isWaitingResult()) {
                setRunStatus("Waiting for interaction", "waiting");
            } else {
                setRunStatus("Completed", "");
            }
            render();
        } catch (error) {
            console.error(error);
            state.loading = false;
            state.interactionSubmitting = "";
            state.streaming.assistantText = "Resume failed: " + readErrorMessage(error);
            setRunStatus("Resume failed", "failed");
            render();
        }
    }

    function buildInteractionPayload(interaction, action) {
        const payload = interaction?.payload || {};
        const interactionType = String(interaction?.interactionType || "").toUpperCase();
        if (interactionType === "USER_INPUT") {
            const inputKey = String(payload.inputKey || "value");
            const inputElement = document.querySelector("[data-interaction-input]");
            const value = String(inputElement?.value || "").trim();
            return {
                [inputKey]: value,
                value: value
            };
        }

        if (interactionType === "HUMAN_CONFIRM") {
            const feedbackElement = document.querySelector("[data-interaction-feedback]");
            return {
                action: action,
                feedback: String(feedbackElement?.value || "").trim()
            };
        }

        return { action: action };
    }

    function interactionConversationMessage(interaction, payload) {
        const interactionType = String(interaction?.interactionType || "").toUpperCase();
        if (interactionType !== "USER_INPUT") {
            return null;
        }
        const value = String(payload?.value || "").trim();
        if (!value) {
            return null;
        }
        return {
            role: "user",
            message: value
        };
    }

    function buildWorkflowRequest(userMessage, conversationOverride) {
        const conversation = buildConversationForRequest(userMessage, conversationOverride);
        if (state.workflowMode === "workflow") {
            return buildNodeOrchestrationWorkflowRequest(userMessage, conversation);
        }

        const selectedToolDefinitions = state.mcpTools.filter(tool => state.selectedTools.has(tool.name));
        const hasSelectedTools = selectedToolDefinitions.length > 0;

        if (!hasSelectedTools) {
            return {
                workflowId: "studio-chat-" + Date.now(),
                workflowName: "Studio Chat Agent",
                description: "Direct chat mode without MCP tools.",
                userMessage: userMessage,
                asyncMode: true,
                conversation: conversation.map(toConversationMessage),
                nodes: [
                    startNode("start-1", userMessage),
                    {
                        id: "agent-chat-1",
                        type: "agent",
                        name: "Chat Agent",
                        description: "General chat without MCP tools.",
                        params: {
                            agent_stage: "chat",
                            system_prompt: "You are the general chat agent in a real workflow platform. Answer clearly, stay grounded in the visible conversation, and be explicit when no external MCP tools are selected.",
                            final_target: "end-1",
                            user_message_ref: "start-1.guide_word"
                        }
                    },
                    endNode("end-1")
                ],
                edges: [
                    edge("start-1", "agent-chat-1"),
                    edge("agent-chat-1", "end-1", "final")
                ]
            };
        }

        const toolTargetMap = {};
        const toolNodes = selectedToolDefinitions.map((tool, index) => {
            const nodeId = "tool-" + (index + 1);
            toolTargetMap[tool.name] = nodeId;
            return {
                id: nodeId,
                type: "tool",
                name: tool.name,
                description: tool.description || ("Execute " + tool.name),
                params: {
                    tool_name: tool.name,
                    tool_args: {}
                }
            };
        });

        const nodes = [
            startNode("start-1", userMessage),
            {
                id: "agent-plan-1",
                type: "agent",
                name: "Planner Agent",
                description: "Select the next MCP tools or finish.",
                params: {
                    agent_stage: "plan",
                    system_prompt: "You are the planner in a real agent platform. Use only the selected MCP tools when they materially help answer the user. Prefer the minimum tool set needed. When the observed results are enough, stop calling tools and hand off to the synthesizer.",
                    assistant_message: "Planning next tool step.",
                    react_loop_enabled: true,
                    react_max_iterations: 4,
                    final_target: "agent-summarize-1",
                    tool_names: selectedToolDefinitions.map(tool => tool.name),
                    tool_target_map: toolTargetMap,
                    user_message_ref: "start-1.guide_word"
                }
            }
        ].concat(toolNodes).concat([
            {
                id: "runwait-1",
                type: "runwait",
                name: "Join Tool Results",
                description: "Wait for selected tools to finish."
            },
            {
                id: "agent-summarize-1",
                type: "agent",
                name: "Synthesis Agent",
                description: "Build the final answer from tool observations.",
                params: {
                    agent_stage: "synthesize",
                    system_prompt: "You are the final answer agent. Synthesize the latest tool observations into a concise, helpful response. Mention tool-based evidence when it matters and stay honest about missing data.",
                    require_human_confirmation: true,
                    final_target: "end-1",
                    user_message_ref: "start-1.guide_word"
                }
            },
            endNode("end-1")
        ]);

        const edges = [
            edge("start-1", "agent-plan-1"),
            edge("agent-plan-1", "agent-summarize-1", "final"),
            edge("runwait-1", "agent-plan-1"),
            edge("agent-summarize-1", "end-1", "final")
        ];

        toolNodes.forEach(toolNode => {
            edges.push(edge("agent-plan-1", toolNode.id, "tool"));
            edges.push(edge(toolNode.id, "runwait-1"));
        });

        return {
            workflowId: "studio-react-" + Date.now(),
            workflowName: "Studio ReAct Agent",
            description: "Generic chat agent with selected MCP tools.",
            userMessage: userMessage,
            asyncMode: true,
            conversation: conversation.map(toConversationMessage),
            nodes: nodes,
            edges: edges
        };
    }

    function buildConversationForRequest(userMessage, conversationOverride) {
        if (Array.isArray(conversationOverride)) {
            return conversationOverride.slice();
        }
        const conversation = state.conversation.slice();
        const normalizedUserMessage = String(userMessage || "").trim();
        if (!normalizedUserMessage) {
            return conversation;
        }
        const lastMessage = conversation.length ? conversation[conversation.length - 1] : null;
        if (lastMessage && lastMessage.role === "user" && String(lastMessage.message || "").trim() === normalizedUserMessage) {
            return conversation;
        }
        return conversation.concat([{
            role: "user",
            message: normalizedUserMessage
        }]);
    }

    function buildNodeOrchestrationWorkflowRequestLegacy(userMessage) {
        return {
            workflowId: "studio-workflow-" + Date.now(),
            workflowName: "Studio Workflow Orchestration Demo",
            description: "Nodes-only workflow that showcases dual parallel branching, conditional recovery, runwait joins, and final aggregation.",
            userMessage: userMessage,
            asyncMode: true,
            conversation: state.conversation.map(toConversationMessage),
            nodes: [
                startNode("start-1", "Receive user request"),
                {
                    id: "output-brief-1",
                    type: "output",
                    name: "Shared Brief",
                    description: "A shared branch that always runs before the decision branches converge.",
                    message: "Shared brief: the workflow captured '{{#start-1.guide_word#}}' and opened a planning record for this request.",
                    delayMs: 220
                },
                {
                    id: "condition-pace-1",
                    type: "condition",
                    name: "Pace Router",
                    description: "Choose a travel pace branch based on the user request.",
                    params: {
                        default_target: "output-pace-balanced-1",
                        conditions: [
                            {
                                name: "pace_relaxed",
                                source: "start-1.guide_word",
                                operator: "contains",
                                value: "轻松",
                                target: "output-pace-relaxed-1"
                            },
                            {
                                name: "pace_efficiency",
                                source: "start-1.guide_word",
                                operator: "contains",
                                value: "高效",
                                target: "output-pace-efficient-1"
                            }
                        ]
                    }
                },
                {
                    id: "condition-weather-1",
                    type: "condition",
                    name: "Weather Router",
                    description: "Choose an environmental handling branch based on the user request.",
                    params: {
                        default_target: "output-weather-standard-1",
                        conditions: [
                            {
                                name: "rain_recovery",
                                source: "start-1.guide_word",
                                operator: "contains",
                                value: "下雨",
                                target: "output-weather-rain-1"
                            },
                            {
                                name: "rain_recovery_short",
                                source: "start-1.guide_word",
                                operator: "contains",
                                value: "雨",
                                target: "output-weather-rain-1"
                            },
                            {
                                name: "indoor_bias",
                                source: "start-1.guide_word",
                                operator: "contains",
                                value: "室内",
                                target: "output-weather-rain-1"
                            }
                        ]
                    }
                },
                {
                    id: "output-pace-relaxed-1",
                    type: "output",
                    name: "Relaxed Pace Branch",
                    description: "Relaxed pace branch.",
                    message: "Pace branch: keep each day to two major stops, add meal and rest buffers, and avoid dense same-day transfers.",
                    delayMs: 160
                },
                {
                    id: "output-pace-efficient-1",
                    type: "output",
                    name: "Efficient Pace Branch",
                    description: "Higher-efficiency pace branch.",
                    message: "Pace branch: cluster attractions by area, start early, and compress transfer windows to maximize on-site time.",
                    delayMs: 160
                },
                {
                    id: "output-pace-balanced-1",
                    type: "output",
                    name: "Balanced Pace Branch",
                    description: "Fallback pace branch when no specific pace preference is detected.",
                    message: "Pace branch: balance landmark density with one flexible evening block and moderate transfer intensity.",
                    delayMs: 160
                },
                {
                    id: "output-weather-rain-1",
                    type: "output",
                    name: "Rain Handling Branch",
                    description: "Rain-aware branch.",
                    message: "Weather branch: bias toward museums, indoor malls, covered transit, and shorter outdoor hops.",
                    delayMs: 260
                },
                {
                    id: "output-weather-standard-1",
                    type: "output",
                    name: "Standard Weather Branch",
                    description: "Default environmental branch when no rain or indoor signal is present.",
                    message: "Weather branch: keep a mixed indoor-outdoor itinerary with normal transfer assumptions.",
                    delayMs: 260
                },
                {
                    id: "runwait-plan-1",
                    type: "runwait",
                    name: "Join Planning Branches",
                    description: "Wait for the shared brief, one pace branch, and one weather branch."
                },
                {
                    id: "condition-recovery-1",
                    type: "condition",
                    name: "Recovery Router",
                    description: "Choose whether the workflow should enter a fallback recovery step after the planning branches join.",
                    params: {
                        default_target: "output-recovery-standard-1",
                        conditions: [
                            {
                                name: "recovery_needed",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "rain_recovery",
                                target: "output-recovery-rain-1"
                            },
                            {
                                name: "recovery_needed_short",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "rain_recovery_short",
                                target: "output-recovery-rain-1"
                            },
                            {
                                name: "recovery_indoor",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "indoor_bias",
                                target: "output-recovery-rain-1"
                            }
                        ]
                    }
                },
                {
                    id: "output-recovery-rain-1",
                    type: "output",
                    name: "Rain Recovery Step",
                    description: "Fallback response when environmental risk is detected.",
                    message: "Recovery branch: switch one outdoor slot to an indoor alternative, leave buffer for taxi or metro transfers, and keep umbrellas in the mandatory checklist.",
                    delayMs: 120
                },
                {
                    id: "output-recovery-standard-1",
                    type: "output",
                    name: "Standard Recovery Step",
                    description: "Normal continuation when no fallback handling is required.",
                    message: "Recovery branch: no environmental fallback was required, so the workflow keeps the standard itinerary shape.",
                    delayMs: 120
                },
                {
                    id: "output-summary-1",
                    type: "output",
                    name: "Final Workflow Summary",
                    description: "Compose the final answer from all prior workflow branches.",
                    message: "Workflow orchestration showcase for '{{#start-1.guide_word#}}'.\n\nPace route: {{#condition-pace-1.matched_rule#}}\nWeather route: {{#condition-weather-1.matched_rule#}}\nRecovery route: {{#condition-recovery-1.matched_rule#}}\n\n{{#output-brief-1.message#}}\n{{#output-pace-relaxed-1.message#}}{{#output-pace-efficient-1.message#}}{{#output-pace-balanced-1.message#}}\n{{#output-weather-rain-1.message#}}{{#output-weather-standard-1.message#}}\n{{#output-recovery-rain-1.message#}}{{#output-recovery-standard-1.message#}}\n\nThis answer was assembled by workflow nodes only: dual parallel branches, one join, one conditional recovery step, and one final aggregation node."
                },
                {
                    id: "end-1",
                    type: "end",
                    name: "End",
                    description: "Finish the workflow.",
                    params: {
                        output_variable: [
                            {
                                key: "final_answer",
                                value: "output-summary-1.message",
                                type: "ref"
                            },
                            {
                                key: "pace_branch",
                                value: "condition-pace-1.matched_rule",
                                type: "ref"
                            },
                            {
                                key: "weather_branch",
                                value: "condition-weather-1.matched_rule",
                                type: "ref"
                            },
                            {
                                key: "recovery_branch",
                                value: "condition-recovery-1.matched_rule",
                                type: "ref"
                            }
                        ]
                    }
                }
            ],
            edges: [
                edge("start-1", "output-brief-1"),
                edge("start-1", "condition-pace-1"),
                edge("start-1", "condition-weather-1"),
                edge("condition-pace-1", "output-pace-relaxed-1", "branch"),
                edge("condition-pace-1", "output-pace-efficient-1", "branch"),
                edge("condition-pace-1", "output-pace-balanced-1", "branch"),
                edge("condition-weather-1", "output-weather-rain-1", "branch"),
                edge("condition-weather-1", "output-weather-standard-1", "branch"),
                edge("output-brief-1", "runwait-plan-1"),
                edge("output-pace-relaxed-1", "runwait-plan-1"),
                edge("output-pace-efficient-1", "runwait-plan-1"),
                edge("output-pace-balanced-1", "runwait-plan-1"),
                edge("output-weather-rain-1", "runwait-plan-1"),
                edge("output-weather-standard-1", "runwait-plan-1"),
                edge("runwait-plan-1", "condition-recovery-1"),
                edge("condition-recovery-1", "output-recovery-rain-1", "branch"),
                edge("condition-recovery-1", "output-recovery-standard-1", "branch"),
                edge("output-recovery-rain-1", "output-summary-1"),
                edge("output-recovery-standard-1", "output-summary-1"),
                edge("output-summary-1", "end-1")
            ]
        };
    }

    function toConversationMessage(item) {
        return {
            role: item.role,
            message: item.message
        };
    }

    function startNode(id, guideWord) {
        return {
            id: id,
            type: "start",
            name: "Start",
            description: "Entry point for the current user request.",
            guideWord: guideWord,
            guideQuestions: []
        };
    }

    function endNode(id) {
        return {
            id: id,
            type: "end",
            name: "End",
            description: "Finish the workflow."
        };
    }

    function edge(source, target, sourceHandle) {
        const item = {
            source: source,
            target: target
        };
        if (sourceHandle) {
            item.sourceHandle = sourceHandle;
        }
        return item;
    }

    async function streamWorkflow(request) {
        const response = await fetch("/api/demo/workflows/chat/stream", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "text/event-stream"
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw await responseToError(response);
        }
        if (!response.body) {
            throw new Error("Streaming response body is missing.");
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";

        while (true) {
            const chunk = await reader.read();
            if (chunk.done) {
                break;
            }
            buffer += decoder.decode(chunk.value, { stream: true });
            buffer = consumeSseBuffer(buffer);
        }

        buffer += decoder.decode();
        consumeSseBuffer(buffer, true);
    }

    function consumeSseBuffer(buffer, flushTail) {
        let remaining = buffer;
        while (true) {
            const delimiterIndex = remaining.indexOf("\n\n");
            const windowsDelimiterIndex = remaining.indexOf("\r\n\r\n");
            let boundary = -1;
            let delimiterLength = 2;

            if (windowsDelimiterIndex >= 0 && (delimiterIndex < 0 || windowsDelimiterIndex < delimiterIndex)) {
                boundary = windowsDelimiterIndex;
                delimiterLength = 4;
            } else if (delimiterIndex >= 0) {
                boundary = delimiterIndex;
                delimiterLength = 2;
            }

            if (boundary < 0) {
                break;
            }

            const block = remaining.slice(0, boundary);
            remaining = remaining.slice(boundary + delimiterLength);
            handleSseBlock(block);
        }

        if (flushTail && remaining.trim()) {
            handleSseBlock(remaining);
            return "";
        }

        return remaining;
    }

    function handleSseBlock(block) {
        const lines = block.split(/\r?\n/);
        let eventName = "message";
        const dataLines = [];

        lines.forEach(line => {
            if (!line || line.startsWith(":")) {
                return;
            }
            if (line.startsWith("event:")) {
                eventName = line.slice(6).trim();
                return;
            }
            if (line.startsWith("data:")) {
                dataLines.push(line.slice(5).trim());
            }
        });

        if (!dataLines.length) {
            return;
        }

        const rawData = dataLines.join("\n");
        let payload = rawData;
        try {
            payload = JSON.parse(rawData);
        } catch (error) {
        }

        handleStreamEvent(eventName, payload);
    }

    function handleStreamEvent(eventName, payload) {
        state.streaming.timeline.push({
            event: eventName,
            payload: summarizeTimelinePayload(eventName, payload),
            time: new Date().toISOString()
        });
        if (state.streaming.timeline.length > MAX_TIMELINE_ITEMS) {
            state.streaming.timeline.splice(0, state.streaming.timeline.length - MAX_TIMELINE_ITEMS);
        }

        switch (eventName) {
            case "meta":
                break;
            case "agent_start":
                if (payload?.nodeId) {
                    state.streaming.agentStages[payload.nodeId] = normalizeAgentStage(payload.stage);
                }
                break;
            case "log":
                state.streaming.logs += 1;
                break;
            case "delta":
                if (shouldDisplayAssistantPayload(payload)) {
                    state.streaming.assistantText += String(payload?.content || "");
                }
                break;
            case "agent_complete":
                if (shouldDisplayAssistantPayload(payload) && !state.streaming.assistantText) {
                    state.streaming.assistantText = String(payload?.content || "");
                }
                break;
            case "trace":
                state.streaming.trace.push(payload);
                break;
            case "tool_call":
                state.streaming.toolCalls.push(payload);
                break;
            case "tool_result":
                state.streaming.toolResults.push(payload);
                break;
            case "interaction":
                state.streaming.interactionContext = payload || null;
                state.runResult = Object.assign({}, state.runResult || {}, {
                    interactionContext: payload || null
                });
                break;
            case "result":
                state.runResult = payload;
                state.streaming.interactionContext = payload?.interactionContext || null;
                break;
            case "done":
                if (!state.runResult && payload && typeof payload === "object") {
                    state.runResult = {
                        assistantReply: payload.assistantReply || "",
                        status: payload.status || "COMPLETED"
                    };
                }
                break;
            case "error":
                throw new Error(readPayloadMessage(payload, "Streaming workflow failed."));
            default:
                break;
        }

        scheduleRender();
    }

    function readAssistantReply() {
        const streamedAnswer = String(state.streaming.assistantText || "").trim();
        const fromResult = String(state.runResult?.assistantReply || "").trim();
        if (streamedAnswer && looksLikePlannerInternalText(fromResult)) {
            return streamedAnswer;
        }
        if (fromResult) {
            return fromResult;
        }
        return streamedAnswer;
    }

    function render() {
        updateComposerForMode();
        renderHero();
        renderServers();
        renderToolGroups();
        renderConversation();
        renderInlineInteractionSlot();
        renderInteractionPanel();
        renderRunPanels();
        renderSummaryCards();
        renderImportStatus();
    }

    function renderHero() {
        const llm = state.metadata?.llm || {};
        const provider = String(llm.provider || "LLM");
        const model = String(llm.model || "unknown");
        elements.heroLlm.textContent = provider + " / " + model;
        elements.heroLlmNote.textContent = llm.configured
            ? "Configured. Keys can be changed in application-demo.yml."
            : "Not configured.";
        elements.heroServerCount.textContent = String(state.mcpServers.length);
        elements.heroToolCount.textContent = String(state.mcpTools.length);
        elements.heroSelectedCount.textContent = String(state.selectedTools.size);
        elements.selectedToolsPill.textContent = state.selectedTools.size + " selected";
    }

    function renderServers() {
        elements.serverList.innerHTML = "";

        if (!state.mcpServers.length) {
            elements.serverList.appendChild(emptyState("No MCP servers discovered yet."));
            return;
        }

        state.mcpServers
            .slice()
            .sort((left, right) => String(left.name).localeCompare(String(right.name)))
            .forEach(server => {
                const card = document.createElement("article");
                card.className = "server-card";

                const status = String(server.status || "CONFIGURED").toLowerCase();
                const importedCount = Array.isArray(server.importedTools) ? server.importedTools.length : 0;
                const configuredCount = Array.isArray(server.configuredTools) ? server.configuredTools.length : 0;

                card.innerHTML = [
                    "<div class=\"server-meta\">",
                    "<div>",
                    "<small>" + escapeHtml(String(server.transport || "unknown").toUpperCase()) + "</small>",
                    "<strong>" + escapeHtml(String(server.name || "-")) + "</strong>",
                    "<div class=\"helper\">" + escapeHtml(String(server.endpoint || server.url || "-")) + "</div>",
                    "</div>",
                    "<div class=\"row-actions\">",
                    "<span class=\"server-status " + escapeHtml(status) + "\">" + escapeHtml(String(server.status || "CONFIGURED")) + "</span>",
                    "<button class=\"btn-secondary\" type=\"button\" data-server-refresh=\"" + escapeHtmlAttr(String(server.name || "")) + "\">Refresh</button>",
                    "</div>",
                    "</div>",
                    "<div class=\"helper\" style=\"margin-top: 10px;\">Source: " + escapeHtml(String(server.source || "-")) + "</div>",
                    "<div class=\"helper\">Imported tools: " + importedCount + " | Configured fallback: " + configuredCount + "</div>",
                    server.lastImportedAt ? "<div class=\"helper\">Last import: " + escapeHtml(server.lastImportedAt) + "</div>" : "",
                    server.lastCheckedAt ? "<div class=\"helper\">Last check: " + escapeHtml(server.lastCheckedAt) + "</div>" : "",
                    server.lastError ? "<div class=\"helper\" style=\"color: #b3432f;\">Last error: " + escapeHtml(server.lastError) + "</div>" : ""
                ].join("");

                elements.serverList.appendChild(card);
            });
    }

    function renderToolGroups() {
        elements.toolGroups.innerHTML = "";

        if (!state.mcpTools.length) {
            elements.toolGroups.appendChild(emptyState("No real MCP tools are available yet."));
            return;
        }

        const grouped = new Map();
        state.mcpTools.forEach(tool => {
            const serverName = tool.serverName || "unknown";
            if (!grouped.has(serverName)) {
                grouped.set(serverName, []);
            }
            grouped.get(serverName).push(tool);
        });

        Array.from(grouped.entries())
            .sort((left, right) => left[0].localeCompare(right[0]))
            .forEach(([serverName, tools]) => {
                const serverStatus = serverStatusMap().get(serverName);
                const group = document.createElement("article");
                group.className = "tool-group";

                const body = document.createElement("div");
                body.innerHTML = [
                    "<div class=\"tool-entry-head\">",
                    "<div>",
                    "<small>Server</small>",
                    "<strong>" + escapeHtml(serverName) + "</strong>",
                    "<div class=\"helper\">" + escapeHtml(String(serverStatus?.status || "UNKNOWN")) + "</div>",
                    "</div>",
                    "<span class=\"pill\">" + tools.length + " tools</span>",
                    "</div>"
                ].join("");
                group.appendChild(body);

                tools.forEach(tool => {
                    const entry = document.createElement("div");
                    entry.className = "tool-entry";

                    const description = String(tool.description || "No description");
                    const schemaText = formatJson(tool.inputSchema || {});
                    const schemaId = "schema-" + toDomSafeId(tool.name);
                    const schemaSummary = summarizeSchema(tool.inputSchema || {});
                    entry.innerHTML = [
                        "<label>",
                        "<input type=\"checkbox\" data-tool-name=\"" + escapeHtmlAttr(tool.name) + "\" " + (state.selectedTools.has(tool.name) ? "checked" : "") + ">",
                        "<div style=\"width: 100%;\">",
                        "<div class=\"tool-entry-head\">",
                        "<div>",
                        "<strong>" + escapeHtml(tool.name) + "</strong>",
                        "<div class=\"helper\">" + escapeHtml(description) + "</div>",
                        "</div>",
                        "<span class=\"badge\">" + escapeHtml(serverName) + "</span>",
                        "</div>",
                        "<div class=\"row-actions\">",
                        "<small>" + escapeHtml(schemaSummary) + "</small>",
                        "<button class=\"btn-secondary\" type=\"button\" data-schema-toggle=\"" + escapeHtmlAttr(schemaId) + "\" aria-expanded=\"false\">View schema</button>",
                        "</div>",
                        "<pre id=\"" + escapeHtmlAttr(schemaId) + "\" class=\"tool-schema\" data-loaded=\"\" data-schema-text=\"" + escapeHtmlAttr(schemaText) + "\" hidden></pre>",
                        "</div>",
                        "</label>"
                    ].join("");
                    group.appendChild(entry);
                });

                elements.toolGroups.appendChild(group);
            });
    }

    function renderConversation() {
        elements.chatHistory.innerHTML = "";
        const messages = state.conversation.slice();

        if (state.loading || state.streaming.assistantText) {
            messages.push({
                role: "assistant",
                message: state.streaming.assistantText || "Working...",
                streaming: state.loading
            });
        }

        if (!messages.length) {
            elements.chatHistory.appendChild(emptyState("Start a new conversation. Selected MCP tools will be exposed to the agent automatically."));
            return;
        }

        messages.forEach(message => {
            const article = document.createElement("article");
            article.className = "chat-message " + (message.role === "assistant" ? "assistant" : "user");
            if (message.streaming) {
                article.className += " streaming";
            }
            article.innerHTML = [
                "<div class=\"chat-message-header\">",
                "<small>" + escapeHtml(message.role === "assistant" ? "Assistant" : "User") + "</small>",
                message.streaming ? "<small>Streaming</small>" : "",
                "</div>",
                "<div class=\"chat-message-body\">" + escapeHtml(String(message.message || "")) + "</div>"
            ].join("");
            elements.chatHistory.appendChild(article);
        });

        elements.chatHistory.scrollTop = elements.chatHistory.scrollHeight;
    }

    function renderInlineInteractionSlot() {
        if (!elements.chatInteractionSlot) {
            return;
        }
        const interaction = currentInteractionContext();
        elements.chatInteractionSlot.innerHTML = "";
        if (!interaction) {
            return;
        }

        const payload = interaction.payload || {};
        const type = String(interaction.interactionType || "").toUpperCase();
        const nodeLabel = String(interaction.nodeName || interaction.nodeId || "Unknown node");
        const reason = String(interaction.reason || "The workflow is waiting for interaction.");
        const reviewType = String(payload.reviewType || "").toUpperCase();
        const submitting = String(state.interactionSubmitting || "").toLowerCase();
        const isResuming = state.loading && !!submitting;

        const card = document.createElement("article");
        card.className = "chat-message assistant";

        if (type === "USER_INPUT") {
            const label = String(payload.label || "Input");
            const placeholder = String(payload.placeholder || "");
            card.innerHTML = [
                "<div class=\"chat-message-header\">",
                "<small>Assistant</small>",
                "<small>Waiting For Input</small>",
                "</div>",
                "<div class=\"chat-message-body\"><strong>" + escapeHtml(nodeLabel) + "</strong>\n" + escapeHtml(reason) + "</div>",
                "<div class=\"field-row\" style=\"margin-top: 12px;\">",
                "<label>" + escapeHtml(label) + "</label>",
                "<textarea data-interaction-input placeholder=\"" + escapeHtmlAttr(placeholder) + "\"></textarea>",
                "</div>",
                "<div class=\"interaction-actions\">",
                "<button class=\"btn-primary\" type=\"button\" data-resume-action=\"continue\" " + (isResuming ? "disabled" : "") + ">" + escapeHtml(isResuming ? "Resuming..." : "Continue Workflow") + "</button>",
                "</div>"
            ].join("");
            elements.chatInteractionSlot.appendChild(card);
            return;
        }

        if (type === "HUMAN_CONFIRM") {
            const approveLabel = reviewType === "FINAL_PLAN" ? "Approve Final Plan" : "Approve And Continue";
            const rejectLabel = reviewType === "FINAL_PLAN" ? "Reject Final Plan" : "Reject Plan";
            const answerBlock = reviewType === "FINAL_PLAN"
                ? "<div class=\"field-row\"><label>Draft final plan</label><div class=\"code-box\">" + escapeHtml(String(payload.finalAnswer || payload.assistantMessage || "No final plan.")) + "</div></div>"
                : "";
            card.innerHTML = [
                "<div class=\"chat-message-header\">",
                "<small>Assistant</small>",
                "<small>" + escapeHtml(reviewType === "FINAL_PLAN" ? "Waiting For Final Approval" : "Waiting For Confirmation") + "</small>",
                "</div>",
                "<div class=\"chat-message-body\"><strong>" + escapeHtml(nodeLabel) + "</strong>\n" + escapeHtml(reason) + "</div>",
                answerBlock,
                "<div class=\"field-row\" style=\"margin-top: 12px;\">",
                "<label>Planner message</label>",
                "<div class=\"code-box\">" + escapeHtml(String(payload.assistantMessage || "No planner message.")) + "</div>",
                "</div>",
                "<div class=\"field-row\">",
                "<label>Selected targets</label>",
                "<div class=\"code-box\">" + escapeHtml(formatJson(payload.selectedTargets || [])) + "</div>",
                "</div>",
                "<div class=\"field-row\">",
                "<label>Proposed tool calls</label>",
                "<div class=\"code-box\">" + escapeHtml(formatJson(payload.toolCalls || [])) + "</div>",
                "</div>",
                "<div class=\"field-row\">",
                "<label>Feedback (optional)</label>",
                "<textarea data-interaction-feedback placeholder=\"Add review notes before approving or rejecting.\"></textarea>",
                "</div>",
                "<div class=\"interaction-actions\">",
                "<button class=\"btn-primary\" type=\"button\" data-resume-action=\"approve\" " + (isResuming ? "disabled" : "") + ">" + escapeHtml(isResuming && submitting === "approve" ? "Resuming..." : approveLabel) + "</button>",
                "<button class=\"btn-secondary\" type=\"button\" data-resume-action=\"reject\" " + (isResuming ? "disabled" : "") + ">" + escapeHtml(isResuming && submitting === "reject" ? "Resuming..." : rejectLabel) + "</button>",
                "</div>"
            ].join("");
            elements.chatInteractionSlot.appendChild(card);
            return;
        }

        card.innerHTML = [
            "<div class=\"chat-message-header\">",
            "<small>Assistant</small>",
            "<small>Waiting For Interaction</small>",
            "</div>",
            "<div class=\"chat-message-body\"><strong>" + escapeHtml(nodeLabel) + "</strong>\n" + escapeHtml(reason) + "</div>",
            "<div class=\"code-box\" style=\"margin-top: 12px;\">" + escapeHtml(formatJson(payload)) + "</div>",
            "<div class=\"interaction-actions\" style=\"margin-top: 12px;\">",
            "<button class=\"btn-primary\" type=\"button\" data-resume-action=\"continue\">Continue Workflow</button>",
            "</div>"
        ].join("");
        elements.chatInteractionSlot.appendChild(card);
    }

    function renderRunPanels() {
        const result = state.runResult || {};
        const traceItems = Array.isArray(result.agentTrace) ? result.agentTrace : state.streaming.trace;
        const toolCalls = Array.isArray(result.toolCalls) ? result.toolCalls : state.streaming.toolCalls;
        const toolResults = Array.isArray(result.toolResults) ? result.toolResults : state.streaming.toolResults;
        const timeline = state.streaming.timeline;
        const variables = result.variables || {};

        elements.metricSteps.textContent = String(result.stepsExecuted || 0);
        elements.metricLogs.textContent = String(result.logs ? result.logs.length : state.streaming.logs);
        elements.metricCheckpoint.textContent = String(result.checkpointId || "-");
        elements.variablesView.textContent = formatJson(variables);

        renderList(elements.traceList, traceItems, renderTraceCard, "No trace yet.");
        renderList(elements.toolList, mergeToolCards(toolCalls, toolResults), renderToolCard, "No tool activity yet.");
        renderList(elements.timeline, timeline.slice().reverse(), renderTimelineCard, "No stream events yet.");
    }

    function renderSummaryCards() {
        elements.summaryList.innerHTML = "";

        const llm = state.metadata?.llm || {};
        const healthyServers = state.mcpServers.filter(server => String(server.status || "").toUpperCase() === "HEALTHY").length;
        const mode = readAgentModeLabel();
        const cards = [
            {
                title: "Runtime",
                body: String(llm.provider || "LLM") + " / " + String(llm.model || "unknown")
            },
            {
                title: "Agent mode",
                body: mode
            },
            {
                title: "Healthy servers",
                body: healthyServers + " / " + state.mcpServers.length
            },
            {
                title: "Selected tools",
                body: String(state.selectedTools.size)
            }
        ];

        cards.forEach(item => {
            const article = document.createElement("article");
            article.className = "summary-card";
            article.innerHTML = [
                "<small>" + escapeHtml(item.title) + "</small>",
                "<strong>" + escapeHtml(item.body) + "</strong>"
            ].join("");
            elements.summaryList.appendChild(article);
        });
    }

    function renderImportStatus() {
        const transport = String(elements.importTransport.value || "stdio").toLowerCase();
        const suffix = transport === "stdio"
            ? "Use a stable local command for best reliability."
            : "Use HTTP only when the MCP endpoint already speaks the MCP protocol.";
        elements.importStatus.textContent = state.importStatus + " " + suffix;
    }

    function updateRequestPreview(typedMessage) {
        const previewMessage = typedMessage || "Preview request";
        const cacheKey = JSON.stringify({
            message: previewMessage,
            workflowMode: state.workflowMode,
            selectedTools: Array.from(state.selectedTools).sort(),
            conversationSize: state.conversation.length
        });
        if (cacheKey === state.requestPreviewCacheKey) {
            elements.requestPreview.textContent = state.requestPreviewCacheText || "{}";
            return;
        }
        const preview = buildWorkflowRequest(previewMessage);
        const previewText = formatJson(preview);
        state.requestPreviewCacheKey = cacheKey;
        state.requestPreviewCacheText = previewText;
        elements.requestPreview.textContent = previewText;
    }

    function renderList(container, items, renderer, emptyText) {
        container.innerHTML = "";
        if (!items || !items.length) {
            container.appendChild(emptyState(emptyText));
            return;
        }
        items.forEach(item => container.appendChild(renderer(item)));
    }

    function renderTraceCard(item) {
        const article = document.createElement("article");
        article.className = "trace-card";
        article.innerHTML = [
            "<small>" + escapeHtml(String(item?.nodeId || "trace")) + "</small>",
            "<strong>" + escapeHtml(String(item?.type || item?.decisionType || "trace")) + "</strong>",
            "<div class=\"trace-body\">" + escapeHtml(readPayloadMessage(item?.message || item?.content || item, "")) + "</div>",
            item?.payload ? "<pre class=\"code-box\">" + escapeHtml(formatJson(item.payload)) + "</pre>" : ""
        ].join("");
        return article;
    }

    function renderToolCard(item) {
        const article = document.createElement("article");
        article.className = "tool-card";
        article.innerHTML = [
            "<small>" + escapeHtml(String(item.toolName || "-")) + "</small>",
            "<strong>" + escapeHtml(String(item.phase || "tool")) + "</strong>",
            item.arguments ? "<div class=\"tool-body\">Arguments\n" + escapeHtml(formatJson(item.arguments)) + "</div>" : "",
            item.result ? "<div class=\"tool-body\">Result\n" + escapeHtml(formatJson(item.result)) + "</div>" : "",
            item.summary ? "<div class=\"tool-body\">" + escapeHtml(item.summary) + "</div>" : ""
        ].join("");
        return article;
    }

    function renderTimelineCard(item) {
        const article = document.createElement("article");
        article.className = "timeline-card";
        article.innerHTML = [
            "<small>" + escapeHtml(String(item.time || "")) + "</small>",
            "<strong>" + escapeHtml(String(item.event || "event")) + "</strong>",
            "<div class=\"timeline-body\">" + escapeHtml(formatCompactPayload(item.payload)) + "</div>"
        ].join("");
        return article;
    }

    function scheduleRender() {
        if (state.renderQueued) {
            return;
        }
        state.renderQueued = true;
        window.requestAnimationFrame(() => {
            state.renderQueued = false;
            render();
        });
    }

    function mergeToolCards(toolCalls, toolResults) {
        const cards = [];
        toolCalls.forEach(item => {
            cards.push({
                phase: "call",
                toolName: item?.toolName || item?.name || "-",
                arguments: item?.arguments || item?.toolArgs || {},
                summary: item?.targetNodeId ? ("Target node: " + item.targetNodeId) : ""
            });
        });
        toolResults.forEach(item => {
            cards.push({
                phase: "result",
                toolName: item?.toolName || "-",
                result: item?.result || item,
                summary: readPayloadMessage(item?.result?.content || item?.result?.message || item, "")
            });
        });
        return cards;
    }

    function emptyState(text) {
        const div = document.createElement("div");
        div.className = "empty-state";
        div.textContent = text;
        return div;
    }

    function renderErrorState(message) {
        elements.serverList.innerHTML = "";
        elements.serverList.appendChild(emptyState(message));
    }

    function setRunStatus(text, variant) {
        elements.runStatus.textContent = text;
        elements.runStatus.className = "status-line";
        if (variant) {
            elements.runStatus.classList.add(variant);
        }
    }

    function createStreamingState() {
        return {
            assistantText: "",
            agentStages: {},
            timeline: [],
            trace: [],
            toolCalls: [],
            toolResults: [],
            logs: 0
        };
    }

    function serverStatusMap() {
        return new Map(state.mcpServers.map(server => [server.name, server]));
    }

    async function requestJson(url, options) {
        const response = await fetch(url, options || {});
        if (!response.ok) {
            throw await responseToError(response);
        }
        return response.json();
    }

    async function responseToError(response) {
        try {
            const contentType = String(response.headers.get("content-type") || "");
            if (contentType.includes("application/json")) {
                const payload = await response.json();
                return new Error(readPayloadMessage(payload, "Request failed with HTTP " + response.status));
            }
            const text = await response.text();
            return new Error(text || ("Request failed with HTTP " + response.status));
        } catch (error) {
            return new Error("Request failed with HTTP " + response.status);
        }
    }

    function readPayloadMessage(payload, fallback) {
        if (payload == null) {
            return fallback;
        }
        if (typeof payload === "string") {
            return payload;
        }
        if (typeof payload.message === "string" && payload.message.trim()) {
            return payload.message;
        }
        if (typeof payload.reason === "string" && payload.reason.trim()) {
            return payload.reason;
        }
        if (typeof payload.errorMessage === "string" && payload.errorMessage.trim()) {
            return payload.errorMessage;
        }
        if (typeof payload.assistantReply === "string" && payload.assistantReply.trim()) {
            return payload.assistantReply;
        }
        return fallback || formatCompactPayload(payload);
    }

    function readAgentModeLabel() {
        if (state.workflowMode === "workflow") {
            return "Nodes-only orchestration";
        }
        return state.selectedTools.size > 0 ? "ReAct with MCP tools" : "Direct chat";
    }

    function updateComposerForMode() {
        if (elements.workflowMode && elements.workflowMode.value !== state.workflowMode) {
            elements.workflowMode.value = state.workflowMode;
        }

        if (state.workflowMode === "workflow") {
            elements.chatInput.placeholder = "Example: 我想要一个轻松的南京三天行程，下雨也可以。";
            elements.workflowModeNote.textContent = "This demo ignores MCP tools and showcases pure node orchestration: dual parallel condition branches, one runwait join, one recovery router, and one final summary.";
            return;
        }

        elements.chatInput.placeholder = "Example: Find the coordinates of the Forbidden City, or check Beijing station codes with 12306.";
        elements.workflowModeNote.textContent = "Use selected MCP tools in chat mode, or switch to a nodes-only workflow demo.";
    }

    function shouldDisplayAssistantPayload(payload) {
        const stage = resolveAgentStage(payload);
        if (!stage) {
            return isVisibleAssistantNode(String(payload?.nodeId || ""));
        }
        return stage === "chat" || stage === "synthesize" || stage === "summary";
    }

    function resolveAgentStage(payload) {
        const explicitStage = normalizeAgentStage(payload?.stage);
        if (explicitStage) {
            return explicitStage;
        }
        const nodeId = String(payload?.nodeId || "");
        return normalizeAgentStage(state.streaming.agentStages[nodeId]);
    }

    function normalizeAgentStage(value) {
        return String(value || "").trim().toLowerCase();
    }

    function isVisibleAssistantNode(nodeId) {
        return nodeId.startsWith("agent-chat-")
            || nodeId.startsWith("agent-summarize-")
            || nodeId.startsWith("agent-summary-");
    }

    function looksLikePlannerInternalText(text) {
        const normalized = String(text || "").trim().toLowerCase();
        if (!normalized) {
            return false;
        }
        return normalized.startsWith("based on the collected tool observations:")
            || normalized === "planner selected the next tool step."
            || normalized.startsWith("planning next tool step.");
    }

    function readErrorMessage(error) {
        if (!error) {
            return "Unknown error";
        }
        return typeof error.message === "string" && error.message.trim()
            ? error.message
            : String(error);
    }

    function formatCompactPayload(value) {
        if (value == null) {
            return "";
        }
        if (typeof value === "string") {
            return value;
        }
        try {
            return JSON.stringify(value);
        } catch (error) {
            return String(value);
        }
    }

    function summarizeTimelinePayload(eventName, payload) {
        if (payload == null) {
            return "";
        }
        if (typeof payload === "string") {
            return truncateText(payload, MAX_TIMELINE_PREVIEW_CHARS);
        }

        if (eventName === "delta") {
            return {
                nodeId: payload.nodeId || "",
                content: truncateText(String(payload.content || ""), 120)
            };
        }

        if (eventName === "tool_call") {
            return {
                toolName: payload.toolName || payload.name || "",
                targetNodeId: payload.targetNodeId || "",
                arguments: payload.arguments || payload.toolArgs || {}
            };
        }

        if (eventName === "tool_result") {
            const result = payload.result || {};
            return {
                toolName: payload.toolName || "",
                success: result.success,
                contentPreview: truncateText(readPayloadMessage(result.content || result.message || result, ""), 180)
            };
        }

        if (eventName === "result") {
            return {
                status: payload.status || "",
                stepsExecuted: payload.stepsExecuted || 0,
                assistantReplyPreview: truncateText(String(payload.assistantReply || ""), 180)
            };
        }

        if (eventName === "trace") {
            return {
                nodeId: payload.nodeId || "",
                phase: payload.phase || payload.type || "",
                message: truncateText(String(payload.message || payload.content || ""), 180)
            };
        }

        return truncateText(formatCompactPayload(payload), MAX_TIMELINE_PREVIEW_CHARS);
    }

    function truncateText(value, maxLength) {
        const text = String(value || "");
        if (text.length <= maxLength) {
            return text;
        }
        return text.slice(0, Math.max(0, maxLength - 3)) + "...";
    }

    function formatJson(value) {
        try {
            return JSON.stringify(value == null ? {} : value, null, 2);
        } catch (error) {
            return String(value);
        }
    }

    function summarizeSchema(schema) {
        const properties = schema && typeof schema === "object" && schema.properties && typeof schema.properties === "object"
            ? Object.keys(schema.properties)
            : [];
        const required = Array.isArray(schema?.required) ? schema.required.length : 0;
        return properties.length + " fields, " + required + " required";
    }

    function toDomSafeId(value) {
        return String(value || "")
            .toLowerCase()
            .replaceAll(/[^a-z0-9_-]+/g, "-")
            .replaceAll(/^-+|-+$/g, "") || "schema";
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    function escapeHtmlAttr(value) {
        return escapeHtml(value).replaceAll("`", "&#96;");
    }

    function buildNodeOrchestrationWorkflowRequest(userMessage, conversationOverride) {
        const conversation = buildConversationForRequest(userMessage, conversationOverride);
        return {
            workflowId: "studio-workflow-" + Date.now(),
            workflowName: "Studio Workflow Orchestration Demo",
            description: "Nodes-only workflow that showcases input pause, dual parallel branching, conditional recovery, runwait joins, and final aggregation.",
            userMessage: userMessage,
            asyncMode: true,
            conversation: conversation.map(toConversationMessage),
            nodes: [
                startNode("start-1", userMessage),
                {
                    id: "input-1",
                    type: "input",
                    name: "Collect Missing Detail",
                    description: "Pause the workflow and collect one extra constraint before parallel planning starts.",
                    params: {
                        prompt: "Provide one more detail so the workflow can continue.",
                        input_key: "trip_detail",
                        input_label: "Extra travel detail",
                        placeholder: "Example: budget around 1000, relaxed pace, rain-friendly places, near Nanjing South Railway Station",
                        multiline: true,
                        save_to_chat_context: true
                    }
                },
                {
                    id: "output-brief-1",
                    type: "output",
                    name: "Shared Brief",
                    description: "A shared branch that always runs before the decision branches converge.",
                    message: "Shared brief: base request '{{#start-1.guide_word#}}'. Added detail '{{#input-1.trip_detail#}}'.",
                    delayMs: 220
                },
                {
                    id: "condition-pace-1",
                    type: "condition",
                    name: "Pace Router",
                    description: "Choose a travel pace branch based on the collected extra detail.",
                    params: {
                        default_target: "output-pace-balanced-1",
                        conditions: [
                            {
                                name: "pace_relaxed",
                                source: "input-1.trip_detail",
                                operator: "contains",
                                value: "轻松",
                                target: "output-pace-relaxed-1"
                            },
                            {
                                name: "pace_efficiency",
                                source: "input-1.trip_detail",
                                operator: "contains",
                                value: "高效",
                                target: "output-pace-efficient-1"
                            }
                        ]
                    }
                },
                {
                    id: "condition-weather-1",
                    type: "condition",
                    name: "Weather Router",
                    description: "Choose an environmental handling branch based on the collected extra detail.",
                    params: {
                        default_target: "output-weather-standard-1",
                        conditions: [
                            {
                                name: "rain_recovery",
                                source: "input-1.trip_detail",
                                operator: "contains",
                                value: "下雨",
                                target: "output-weather-rain-1"
                            },
                            {
                                name: "rain_recovery_short",
                                source: "input-1.trip_detail",
                                operator: "contains",
                                value: "雨",
                                target: "output-weather-rain-1"
                            },
                            {
                                name: "indoor_bias",
                                source: "input-1.trip_detail",
                                operator: "contains",
                                value: "室内",
                                target: "output-weather-rain-1"
                            }
                        ]
                    }
                },
                {
                    id: "output-pace-relaxed-1",
                    type: "output",
                    name: "Relaxed Pace Branch",
                    description: "Relaxed pace branch.",
                    message: "Pace branch: keep each day to two major stops, add meal and rest buffers, and avoid dense same-day transfers.",
                    delayMs: 160
                },
                {
                    id: "output-pace-efficient-1",
                    type: "output",
                    name: "Efficient Pace Branch",
                    description: "Higher-efficiency pace branch.",
                    message: "Pace branch: cluster attractions by area, start early, and compress transfer windows to maximize on-site time.",
                    delayMs: 160
                },
                {
                    id: "output-pace-balanced-1",
                    type: "output",
                    name: "Balanced Pace Branch",
                    description: "Fallback pace branch when no specific pace preference is detected.",
                    message: "Pace branch: balance landmark density with one flexible evening block and moderate transfer intensity.",
                    delayMs: 160
                },
                {
                    id: "output-weather-rain-1",
                    type: "output",
                    name: "Rain Handling Branch",
                    description: "Rain-aware branch.",
                    message: "Weather branch: bias toward museums, indoor malls, covered transit, and shorter outdoor hops.",
                    delayMs: 260
                },
                {
                    id: "output-weather-standard-1",
                    type: "output",
                    name: "Standard Weather Branch",
                    description: "Default environmental branch when no rain or indoor signal is present.",
                    message: "Weather branch: keep a mixed indoor-outdoor itinerary with normal transfer assumptions.",
                    delayMs: 260
                },
                {
                    id: "runwait-plan-1",
                    type: "runwait",
                    name: "Join Planning Branches",
                    description: "Wait for the shared brief, one pace branch, and one weather branch."
                },
                {
                    id: "condition-recovery-1",
                    type: "condition",
                    name: "Recovery Router",
                    description: "Choose whether the workflow should enter a fallback recovery step after the planning branches join.",
                    params: {
                        default_target: "output-recovery-standard-1",
                        conditions: [
                            {
                                name: "recovery_needed",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "rain_recovery",
                                target: "output-recovery-rain-1"
                            },
                            {
                                name: "recovery_needed_short",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "rain_recovery_short",
                                target: "output-recovery-rain-1"
                            },
                            {
                                name: "recovery_indoor",
                                source: "condition-weather-1.matched_rule",
                                operator: "equals",
                                value: "indoor_bias",
                                target: "output-recovery-rain-1"
                            }
                        ]
                    }
                },
                {
                    id: "output-recovery-rain-1",
                    type: "output",
                    name: "Rain Recovery Step",
                    description: "Fallback response when environmental risk is detected.",
                    message: "Recovery branch: switch one outdoor slot to an indoor alternative, leave buffer for taxi or metro transfers, and keep umbrellas in the mandatory checklist.",
                    delayMs: 120
                },
                {
                    id: "output-recovery-standard-1",
                    type: "output",
                    name: "Standard Recovery Step",
                    description: "Normal continuation when no fallback handling is required.",
                    message: "Recovery branch: no environmental fallback was required, so the workflow keeps the standard itinerary shape.",
                    delayMs: 120
                },
                {
                    id: "output-summary-1",
                    type: "output",
                    name: "Final Workflow Summary",
                    description: "Compose the final answer from all prior workflow branches.",
                    message: "Workflow-only orchestration result.\n\nOriginal request: {{#start-1.guide_word#}}\nCollected input: {{#input-1.trip_detail#}}\n\nPace route: {{#condition-pace-1.matched_rule#}}\nWeather route: {{#condition-weather-1.matched_rule#}}\nRecovery route: {{#condition-recovery-1.matched_rule#}}\n\n{{#output-brief-1.message#}}\n{{#output-pace-relaxed-1.message#}}{{#output-pace-efficient-1.message#}}{{#output-pace-balanced-1.message#}}\n{{#output-weather-rain-1.message#}}{{#output-weather-standard-1.message#}}\n{{#output-recovery-rain-1.message#}}{{#output-recovery-standard-1.message#}}\n\nThis answer was assembled by workflow nodes only: one input pause, dual parallel branches, one join, one conditional recovery step, and one final aggregation node."
                },
                {
                    id: "end-1",
                    type: "end",
                    name: "End",
                    description: "Finish the workflow.",
                    params: {
                        output_variable: [
                            {
                                key: "final_answer",
                                value: "output-summary-1.message",
                                type: "ref"
                            },
                            {
                                key: "pace_branch",
                                value: "condition-pace-1.matched_rule",
                                type: "ref"
                            },
                            {
                                key: "weather_branch",
                                value: "condition-weather-1.matched_rule",
                                type: "ref"
                            },
                            {
                                key: "recovery_branch",
                                value: "condition-recovery-1.matched_rule",
                                type: "ref"
                            }
                        ]
                    }
                }
            ],
            edges: [
                edge("start-1", "input-1"),
                edge("input-1", "output-brief-1"),
                edge("input-1", "condition-pace-1"),
                edge("input-1", "condition-weather-1"),
                edge("condition-pace-1", "output-pace-relaxed-1", "branch"),
                edge("condition-pace-1", "output-pace-efficient-1", "branch"),
                edge("condition-pace-1", "output-pace-balanced-1", "branch"),
                edge("condition-weather-1", "output-weather-rain-1", "branch"),
                edge("condition-weather-1", "output-weather-standard-1", "branch"),
                edge("output-brief-1", "runwait-plan-1"),
                edge("output-pace-relaxed-1", "runwait-plan-1"),
                edge("output-pace-efficient-1", "runwait-plan-1"),
                edge("output-pace-balanced-1", "runwait-plan-1"),
                edge("output-weather-rain-1", "runwait-plan-1"),
                edge("output-weather-standard-1", "runwait-plan-1"),
                edge("runwait-plan-1", "condition-recovery-1"),
                edge("condition-recovery-1", "output-recovery-rain-1", "branch"),
                edge("condition-recovery-1", "output-recovery-standard-1", "branch"),
                edge("output-recovery-rain-1", "output-summary-1"),
                edge("output-recovery-standard-1", "output-summary-1"),
                edge("output-summary-1", "end-1")
            ]
        };
    }

    function renderInteractionPanel() {
        if (!elements.interactionPanel || !elements.interactionContent) {
            return;
        }
        elements.interactionPanel.hidden = true;
        elements.interactionContent.innerHTML = "";
    }

    function currentInteractionContext() {
        return state.runResult?.interactionContext || state.streaming.interactionContext || null;
    }

    function isWaitingResult() {
        return String(state.runResult?.status || "").toUpperCase() === "WAITING";
    }

    function normalizeToolCalls(value, fallback) {
        if (Array.isArray(value)) {
            return value;
        }
        return Array.isArray(fallback) ? fallback : [];
    }

    function normalizeToolResults(value, fallback) {
        if (Array.isArray(value)) {
            return value;
        }
        if (value && typeof value === "object") {
            return Object.entries(value).map(([toolName, result]) => ({
                toolName: toolName,
                result: result
            }));
        }
        return Array.isArray(fallback) ? fallback : [];
    }
})();
