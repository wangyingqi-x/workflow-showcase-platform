package com.itao.workflowEngine.agent.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.itao.workflowEngine.agent.model.AgentDecision;
import com.itao.workflowEngine.agent.model.AgentDecisionType;
import com.itao.workflowEngine.agent.model.AgentPromptContext;
import com.itao.workflowEngine.agent.model.AgentToolCall;
import com.itao.workflowEngine.agent.tool.ToolDefinition;
import com.itao.workflowEngine.agent.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAiChatModelClient implements ChatModelClient {

    private final ToolRegistry toolRegistry;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String provider;
    private final String model;
    private final String baseUrl;
    private final double temperature;

    public OpenAiChatModelClient(ToolRegistry toolRegistry,
                                 @Value("${llm.api-key:${openai.api-key:}}") String apiKey,
                                 @Value("${llm.provider:${openai.provider:GLM}}") String provider,
                                 @Value("${llm.model:${openai.model:glm-5.1}}") String model,
                                 @Value("${llm.base-url:${openai.base-url:https://open.bigmodel.cn/api/paas/v4}}") String baseUrl,
                                 @Value("${llm.temperature:${openai.temperature:0.2}}") double temperature) {
        this.toolRegistry = toolRegistry;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.provider = provider == null || provider.isBlank() ? "GLM" : provider.trim();
        this.model = model == null || model.isBlank() ? "glm-5.1" : model.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? "https://open.bigmodel.cn/api/paas/v4" : baseUrl.trim())
                .replaceAll("/+$", "");
        this.temperature = temperature;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public AgentDecision chat(AgentPromptContext context) {
        if (apiKey.isBlank()) {
            return fallbackDecision(context);
        }
        String stage = String.valueOf(context.getNodeParams().getOrDefault("agent_stage", "chat")).toLowerCase();
        return switch (stage) {
            case "plan", "planner" -> planDecision(context);
            case "synthesize", "summary" -> synthesizeDecision(context);
            default -> chatDecision(context);
        };
    }

    private AgentDecision fallbackDecision(AgentPromptContext context) {
        String stage = String.valueOf(context.getNodeParams().getOrDefault("agent_stage", "chat")).toLowerCase();
        return switch (stage) {
            case "plan", "planner" -> fallbackPlanDecision(context);
            case "synthesize", "summary" -> fallbackSynthesizeDecision(context);
            default -> fallbackChatDecision(context);
        };
    }

    private AgentDecision chatDecision(AgentPromptContext context) {
        String systemPrompt = stringValue(
                context.getNodeParams().get("system_prompt"),
                "You are an agent node inside a workflow engine. Provide a clear, grounded answer based on the current workflow context."
        );
        List<Map<String, Object>> messages = buildBaseMessages(context, systemPrompt);
        if (messages.stream().noneMatch(item -> "user".equals(item.get("role")))) {
            messages.add(message("user", resolveUserInput(context)));
        }

        String answer = shouldStream(context)
                ? streamChatCompletion(messages, context)
                : extractAssistantText(callChatCompletion(messages, List.of(), false));

        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.FINAL_ANSWER);
        decision.setDecisionSource("REMOTE_LLM_CHAT");
        decision.setAssistantMessage(stringValue(context.getNodeParams().get("assistant_message"), answer));
        decision.setFinalAnswer(answer);
        return decision;
    }

    private AgentDecision planDecision(AgentPromptContext context) {
        List<String> toolNames = parseToolNames(context.getNodeParams().get("tool_names"));
        if (toolNames.isEmpty()) {
            return chatDecision(context);
        }
        if (reachedReactIterationLimit(context)) {
            return finalPlanningDecision(
                    context,
                    buildPlanningLoopFinalAnswer(context),
                    "Reached the configured ReAct iteration limit. Consolidating the available observations."
            );
        }

        String systemPrompt = stringValue(
                context.getNodeParams().get("system_prompt"),
                "You are a planning agent inside a workflow graph. Decide whether to call more tools or finish with a grounded answer based on the current observations."
        );
        List<Map<String, Object>> messages = buildBaseMessages(context, systemPrompt);
        messages.add(message("user", buildPlanningPrompt(context, toolNames)));

        try {
            JSONObject response = callChatCompletion(messages, buildOpenAiTools(toolNames), true);
            String assistantText = extractAssistantText(response);
            List<AgentToolCall> toolCalls = extractToolCalls(response, context, toolNames);
            if (toolCalls.isEmpty()) {
                if (assistantText != null && !assistantText.isBlank()) {
                    return finalPlanningDecision(context, assistantText, assistantText);
                }
                toolCalls = fallbackToolCalls(context, toolNames);
            }
            if (toolCalls.isEmpty() && hasToolObservations(context)) {
                return finalPlanningDecision(
                        context,
                        buildPlanningLoopFinalAnswer(context),
                        "Planner stopped after reviewing the current tool observations."
                );
            }
            if (toolCalls.isEmpty()) {
                return finalPlanningDecision(
                        context,
                        "The planner did not select any tool for the next step.",
                        "Planner stopped without selecting a tool."
                );
            }
            return toolPlanningDecision(assistantText, toolCalls);
        } catch (IllegalStateException nativeToolError) {
            return planDecisionWithJsonFallback(context, toolNames, systemPrompt, nativeToolError);
        }
    }

    private AgentDecision synthesizeDecision(AgentPromptContext context) {
        String systemPrompt = stringValue(
                context.getNodeParams().get("system_prompt"),
                "You are the synthesis agent of a workflow. Combine tool outputs and conversation context into one final answer."
        );
        List<Map<String, Object>> messages = buildBaseMessages(context, systemPrompt);
        messages.add(message("user",
                "Tool observations:\n" + buildToolResultSummary(context) + "\n\nPlease produce the final answer for this workflow run."));

        String answer = shouldStream(context)
                ? streamChatCompletion(messages, context)
                : extractAssistantText(callChatCompletion(messages, List.of(), false));

        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.FINAL_ANSWER);
        decision.setDecisionSource("REMOTE_LLM_SYNTHESIZE");
        decision.setAssistantMessage(stringValue(context.getNodeParams().get("assistant_message"), "Tool results received. Generating the final answer."));
        decision.setFinalAnswer(answer);
        return decision;
    }

    private AgentDecision fallbackPlanDecision(AgentPromptContext context) {
        List<String> toolNames = parseToolNames(context.getNodeParams().get("tool_names"));
        if (toolNames.isEmpty()) {
            return fallbackChatDecision(context);
        }
        if (reachedReactIterationLimit(context)) {
            return finalPlanningDecision(
                    context,
                    buildPlanningLoopFinalAnswer(context),
                    "Reached the configured ReAct iteration limit in local fallback mode."
            );
        }

        String userInput = resolveUserInput(context);
        List<AgentToolCall> selectedCalls = new ArrayList<>();
        for (String toolName : toolNames) {
            if (shouldSkipRepeatedToolCall(context, toolName)) {
                continue;
            }
            if (!shouldSelectTool(toolName, userInput, toolNames)) {
                continue;
            }
            AgentToolCall call = new AgentToolCall();
            call.setId(UUID.randomUUID().toString().substring(0, 8));
            call.setToolName(toolName);
            call.setTargetNodeId(resolveToolTarget(context, toolName));
            call.setArguments(resolveToolArguments(context, toolName, Map.of()));
            selectedCalls.add(call);
        }

        if (selectedCalls.isEmpty()) {
            if (hasToolObservations(context)) {
                return finalPlanningDecision(
                        context,
                        buildPlanningLoopFinalAnswer(context),
                        "Local fallback planner determined that the current tool observations are enough."
                );
            }
            String toolName = firstAvailableToolName(context, toolNames);
            if (toolName.isBlank()) {
                return finalPlanningDecision(
                        context,
                        "The local planner could not identify a suitable next tool.",
                        "Local fallback planner stopped without selecting additional tools."
                );
            }
            AgentToolCall call = new AgentToolCall();
            call.setId(UUID.randomUUID().toString().substring(0, 8));
            call.setToolName(toolName);
            call.setTargetNodeId(resolveToolTarget(context, toolName));
            call.setArguments(resolveToolArguments(context, toolName, Map.of()));
            selectedCalls.add(call);
        }

        if (!selectedCalls.isEmpty()) {
            return toolPlanningDecision("LLM API key is not configured. Switched to local planning heuristics.", selectedCalls);
        }

        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.TOOL_CALLS);
        decision.setAssistantMessage("当前未配置 LLM API Key，已切换为本地演示规划模式。");
        decision.setToolCalls(selectedCalls);
        return decision;
    }

    private AgentDecision fallbackSynthesizeDecision(AgentPromptContext context) {
        String userInput = resolveUserInput(context);
        String summary = buildToolResultSummary(context);
        String answer = context.getToolResults().isEmpty()
                ? "当前没有可用的工具结果，本地演示模式无法继续总结。"
                : "本地演示总结：\n" + summary;

        if (context.getToolResults().size() > 1) {
            answer = "根据多个工具返回结果：\n" + summary;
        } else if (looksLikeWeatherQuestion(userInput)) {
            Map<String, Object> weather = firstToolResult(context, "query_weather", "query_city_weather", "query_air_quality");
            Object content = weather == null ? null : weather.get("content");
            if (content != null) {
                answer = "根据天气工具返回结果，" + content;
            }
        } else if (looksLikeRouteQuestion(userInput) || looksLikeGeocodeQuestion(userInput)) {
            Map<String, Object> route = firstToolResult(context, "calculate_route", "geocode_city");
            Object content = route == null ? null : route.get("content");
            if (content != null) {
                answer = "根据地图工具返回结果，" + content;
            }
        } else if (looksLikeMerchantQuestion(userInput)) {
            Map<String, Object> merchant = firstToolResult(context, "query_credit_score", "query_industry_risk", "query_merchant_risk");
            Object content = merchant == null ? null : merchant.get("content");
            if (content != null) {
                answer = "根据商户工具返回结果，" + content;
            }
        }

        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.FINAL_ANSWER);
        decision.setDecisionSource("LOCAL_FALLBACK_SYNTHESIZE");
        decision.setAssistantMessage("当前未配置 LLM API Key，已切换为本地演示总结模式。");
        decision.setFinalAnswer(answer);
        return decision;
    }

    private AgentDecision fallbackChatDecision(AgentPromptContext context) {
        String userInput = resolveUserInput(context);
        String answer;
        if (looksLikeWeatherQuestion(userInput) || looksLikeRouteQuestion(userInput) || looksLikeMerchantQuestion(userInput)) {
            answer = "当前未配置 LLM API Key。你依然可以运行多 MCP ReAct 蓝图，规划和总结会走本地演示模式；配置好 GLM Key 后会切换为真实模型回复。";
        } else if (userInput == null || userInput.isBlank()) {
            answer = "当前未配置 LLM API Key，已进入本地演示模式。";
        } else {
            answer = "本地演示模式已接管当前 Agent 节点。收到的问题是：“" + userInput + "”。配置好 LLM Key 后会切换为真实模型生成。";
        }

        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.FINAL_ANSWER);
        decision.setDecisionSource("LOCAL_FALLBACK_CHAT");
        decision.setAssistantMessage("当前未配置 LLM API Key，已切换为本地演示回答模式。");
        decision.setFinalAnswer(answer);
        return decision;
    }

    private List<Map<String, Object>> buildBaseMessages(AgentPromptContext context, String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        for (Map<String, String> item : context.getChatContext()) {
            String role = normalizeOpenAiRole(item.get("role"));
            String text = item.get("message");
            if (text != null && !text.isBlank()) {
                messages.add(message(role, text));
            }
        }
        return messages;
    }

    private List<Map<String, Object>> buildOpenAiTools(List<String> toolNames) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (String toolName : toolNames) {
            ToolDefinition definition = toolRegistry.getDefinition(toolName);
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", toolName);
            function.put("description", definition == null ? toolName : definition.getDescription());
            function.put("parameters", definition == null ? defaultToolSchema() : definition.getInputSchema());
            tools.add(Map.of("type", "function", "function", function));
        }
        return tools;
    }

    private JSONObject callChatCompletion(List<Map<String, Object>> messages,
                                          List<Map<String, Object>> tools,
                                          boolean requireTools) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionUrl()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(provider + " request failed: " + extractError(response.body()));
            }
            return JSON.parseObject(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException(provider + " request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(provider + " request interrupted.", ex);
        }
    }

    private String streamChatCompletion(List<Map<String, Object>> messages, AgentPromptContext context) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);
        body.put("stream", true);

        AgentMessageStreamListener listener = context.getStreamListener();
        if (listener != null) {
            listener.onStart(context.getNodeId(), String.valueOf(context.getNodeParams().getOrDefault("agent_stage", "chat")));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionUrl()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException(provider + " request failed: " + extractError(errorBody));
            }
            return consumeStream(response.body(), context);
        } catch (IOException ex) {
            throw new IllegalStateException(provider + " request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(provider + " request interrupted.", ex);
        }
    }

    private String consumeStream(InputStream inputStream, AgentPromptContext context) throws IOException {
        StringBuilder answer = new StringBuilder();
        AgentMessageStreamListener listener = context.getStreamListener();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trimmed.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JSONObject chunk = JSON.parseObject(payload);
                String delta = extractDeltaText(chunk);
                if (delta.isBlank()) {
                    continue;
                }
                answer.append(delta);
                if (listener != null) {
                    listener.onDelta(context.getNodeId(), delta);
                }
            }
        }
        if (listener != null) {
            listener.onComplete(context.getNodeId(), answer.toString());
        }
        return answer.toString();
    }

    private List<AgentToolCall> extractToolCalls(JSONObject response,
                                                 AgentPromptContext context,
                                                 List<String> configuredToolNames) {
        List<AgentToolCall> calls = new ArrayList<>();
        JSONObject message = firstMessage(response);
        if (message == null) {
            return calls;
        }
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls == null) {
            return calls;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            JSONObject rawCall = toolCalls.getJSONObject(index);
            JSONObject function = rawCall.getJSONObject("function");
            if (function == null) {
                continue;
            }
            String toolName = function.getString("name");
            if (toolName == null || !configuredToolNames.contains(toolName) || shouldSkipRepeatedToolCall(context, toolName)) {
                continue;
            }
            AgentToolCall call = new AgentToolCall();
            call.setId(rawCall.getString("id") == null ? UUID.randomUUID().toString().substring(0, 8) : rawCall.getString("id"));
            call.setToolName(toolName);
            call.setTargetNodeId(resolveToolTarget(context, toolName));
            call.setArguments(resolveToolArguments(context, toolName, parseArguments(function.getString("arguments"))));
            calls.add(call);
        }
        return calls;
    }

    private List<AgentToolCall> fallbackToolCalls(AgentPromptContext context, List<String> toolNames) {
        List<AgentToolCall> calls = new ArrayList<>();
        for (String toolName : toolNames) {
            if (shouldSkipRepeatedToolCall(context, toolName)) {
                continue;
            }
            if (!shouldSelectTool(toolName, resolveUserInput(context), toolNames)) {
                continue;
            }
            AgentToolCall call = new AgentToolCall();
            call.setId(UUID.randomUUID().toString().substring(0, 8));
            call.setToolName(toolName);
            call.setTargetNodeId(resolveToolTarget(context, toolName));
            call.setArguments(resolveToolArguments(context, toolName, Map.of()));
            calls.add(call);
        }
        return calls;
    }

    private AgentDecision planDecisionWithJsonFallback(AgentPromptContext context,
                                                       List<String> toolNames,
                                                       String systemPrompt,
                                                       IllegalStateException nativeToolError) {
        List<Map<String, Object>> messages = buildBaseMessages(context, systemPrompt);
        messages.add(message("user", buildJsonPlanningPrompt(context, toolNames)));

        try {
            JSONObject response = callChatCompletion(messages, List.of(), false);
            String answer = extractAssistantText(response);
            String assistantMessage = extractAssistantMessageFromPlanText(answer,
                    "Native tool calling is unavailable for the current provider/model. Switched to JSON planning mode.");
            List<AgentToolCall> toolCalls = extractToolCallsFromPlanText(answer, context, toolNames);
            if (!toolCalls.isEmpty()) {
                return toolPlanningDecision(assistantMessage, toolCalls);
            }
            String finalAnswer = extractFinalAnswerFromPlanText(answer);
            if (finalAnswer != null && !finalAnswer.isBlank()) {
                return finalPlanningDecision(context, finalAnswer, assistantMessage);
            }
            toolCalls = fallbackToolCalls(context, toolNames);
            if (!toolCalls.isEmpty()) {
                return toolPlanningDecision(assistantMessage, toolCalls);
            }
            if (hasToolObservations(context)) {
                return finalPlanningDecision(
                        context,
                        buildPlanningLoopFinalAnswer(context),
                        "JSON planning fallback stopped after reviewing the current tool observations."
                );
            }
            return finalPlanningDecision(context,
                    "The planner could not identify a suitable tool or final answer in JSON fallback mode.",
                    assistantMessage);
        } catch (IllegalStateException jsonFallbackError) {
            AgentDecision decision = fallbackPlanDecision(context);
            String detail = firstMeaningfulError(jsonFallbackError.getMessage(), nativeToolError.getMessage());
            decision.setAssistantMessage("LLM tool planning fallback activated: " + detail);
            return decision;
        }
    }

    private String buildJsonPlanningPrompt(AgentPromptContext context, List<String> toolNames) {
        return """
                You are a planning agent inside a workflow graph.
                Native tool calling may be unavailable for the current model, so you must return a pure JSON object.

                User request:
                %s

                Current ReAct iteration:
                %d / %d

                Current tool observations:
                %s

                Available tools:
                %s

                Return JSON only. Do not wrap it in markdown.
                Expected shape:
                {
                  "assistant_message": "short planning summary for UI",
                  "decision_type": "tool_calls or final_answer",
                  "tool_calls": [
                    {
                      "tool_name": "one of the available tools",
                      "arguments": {
                        "city": "Nanjing"
                      }
                    }
                  ],
                  "final_answer": "required when decision_type is final_answer"
                }

                Rules:
                - Select one or more tools only from the available list.
                - If multiple observations are useful, return multiple tool_calls.
                - arguments must be a JSON object.
                - If the current observations are enough, set decision_type to final_answer, return an empty array, and fill final_answer.
                - Prefer not to repeat a tool that already has an observation unless absolutely necessary.
                """.formatted(
                resolveUserInput(context),
                reactIteration(context),
                reactMaxIterations(context),
                hasToolObservations(context) ? buildToolResultSummary(context) : "No tool observations are available yet.",
                buildToolCatalog(toolNames)
        ).trim();
    }

    private String buildToolCatalog(List<String> toolNames) {
        List<String> rows = new ArrayList<>();
        for (String toolName : toolNames) {
            ToolDefinition definition = toolRegistry.getDefinition(toolName);
            String description = definition == null ? "" : stringValue(definition.getDescription(), "");
            rows.add(toolName + (description.isBlank() ? "" : " - " + description));
        }
        return String.join("\n", rows);
    }

    private String buildPlanningPrompt(AgentPromptContext context, List<String> toolNames) {
        String observations = hasToolObservations(context)
                ? buildToolResultSummary(context)
                : "No tool observations are available yet.";
        String observedTools = hasToolObservations(context)
                ? String.join(", ", context.getToolResults().keySet())
                : "none";
        return """
                User request:
                %s

                Current ReAct iteration:
                %d / %d

                Current tool observations:
                %s

                Already observed tools:
                %s

                Available tools:
                %s

                Decide the next action:
                - If more evidence is required, call one or more tools.
                - If the current observations are already sufficient, do not call more tools and answer directly.

                Rules:
                - Prefer not to repeat a tool that already returned an observation unless absolutely necessary.
                - Keep the tool selection minimal and relevant to the user request.
                - If you answer directly, make it grounded in the current observations.
                """.formatted(
                resolveUserInput(context),
                reactIteration(context),
                reactMaxIterations(context),
                observations,
                observedTools,
                buildToolCatalog(toolNames)
        ).trim();
    }

    private AgentDecision toolPlanningDecision(String assistantMessage, List<AgentToolCall> toolCalls) {
        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.TOOL_CALLS);
        decision.setDecisionSource(apiKey.isBlank() ? "LOCAL_FALLBACK_PLAN" : "REMOTE_LLM_PLAN");
        decision.setAssistantMessage(stringValue(assistantMessage, "Planner selected the next tool step."));
        decision.setToolCalls(toolCalls);
        return decision;
    }

    private AgentDecision finalPlanningDecision(AgentPromptContext context, String finalAnswer, String assistantMessage) {
        AgentDecision decision = new AgentDecision();
        decision.setDecisionType(AgentDecisionType.FINAL_ANSWER);
        decision.setDecisionSource(apiKey.isBlank() ? "LOCAL_FALLBACK_PLAN" : "REMOTE_LLM_PLAN");
        decision.setAssistantMessage(stringValue(assistantMessage, stringValue(finalAnswer, "Planner finished with the available observations.")));
        decision.setFinalAnswer(stringValue(finalAnswer, buildPlanningLoopFinalAnswer(context)));
        return decision;
    }

    private String buildPlanningLoopFinalAnswer(AgentPromptContext context) {
        if (!hasToolObservations(context)) {
            return "No tool observations are available yet.";
        }
        return "Based on the collected tool observations:\n" + buildToolResultSummary(context);
    }

    private List<AgentToolCall> extractToolCallsFromPlanText(String answer,
                                                             AgentPromptContext context,
                                                             List<String> configuredToolNames) {
        List<AgentToolCall> calls = new ArrayList<>();
        JSONObject payload = parseJsonObject(answer);
        if (payload == null) {
            return calls;
        }

        JSONArray toolCalls = payload.getJSONArray("tool_calls");
        if (toolCalls == null) {
            return calls;
        }

        for (int index = 0; index < toolCalls.size(); index++) {
            JSONObject rawCall = toolCalls.getJSONObject(index);
            if (rawCall == null) {
                continue;
            }
            String toolName = stringValue(rawCall.get("tool_name"), stringValue(rawCall.get("name"), ""));
            if (toolName.isBlank() || !configuredToolNames.contains(toolName) || shouldSkipRepeatedToolCall(context, toolName)) {
                continue;
            }

            AgentToolCall call = new AgentToolCall();
            call.setId(stringValue(rawCall.get("id"), UUID.randomUUID().toString().substring(0, 8)));
            call.setToolName(toolName);
            call.setTargetNodeId(resolveToolTarget(context, toolName));
            call.setArguments(resolveToolArguments(context, toolName, jsonObjectToMap(rawCall.get("arguments"))));
            calls.add(call);
        }
        return calls;
    }

    private String extractAssistantMessageFromPlanText(String answer, String defaultValue) {
        JSONObject payload = parseJsonObject(answer);
        if (payload == null) {
            return defaultValue;
        }
        return stringValue(payload.get("assistant_message"),
                stringValue(payload.get("message"),
                        stringValue(payload.get("thought"), defaultValue)));
    }

    private String extractFinalAnswerFromPlanText(String answer) {
        JSONObject payload = parseJsonObject(answer);
        if (payload == null) {
            return "";
        }
        return stringValue(payload.get("final_answer"),
                stringValue(payload.get("finalAnswer"),
                        stringValue(payload.get("answer"), "")));
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JSONObject object = JSON.parseObject(rawArguments);
            Map<String, Object> arguments = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                arguments.put(key, object.get(key));
            }
            return arguments;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private JSONObject parseJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = stripMarkdownFence(text).trim();
        try {
            return JSON.parseObject(normalized);
        } catch (Exception ignored) {
        }

        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return JSON.parseObject(normalized.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String stripMarkdownFence(String text) {
        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            int firstBreak = normalized.indexOf('\n');
            if (firstBreak >= 0) {
                normalized = normalized.substring(firstBreak + 1);
            }
            int lastFence = normalized.lastIndexOf("```");
            if (lastFence >= 0) {
                normalized = normalized.substring(0, lastFence);
            }
        }
        return normalized.trim();
    }

    private Map<String, Object> jsonObjectToMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof JSONObject jsonObject) {
            for (String key : jsonObject.keySet()) {
                result.put(key, jsonObject.get(key));
            }
            return result;
        }
        if (value instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String rawText && !rawText.isBlank()) {
            JSONObject parsed = parseJsonObject(rawText);
            if (parsed != null) {
                return jsonObjectToMap(parsed);
            }
        }
        return result;
    }

    private Map<String, Object> resolveToolArguments(AgentPromptContext context,
                                                     String toolName,
                                                     Map<String, Object> modelArguments) {
        Map<String, Object> arguments = new LinkedHashMap<>(modelArguments);

        Object sharedToolArgs = context.getNodeParams().get("tool_args");
        if (sharedToolArgs instanceof Map<?, ?> rawSharedMap) {
            rawSharedMap.forEach((key, value) -> arguments.putIfAbsent(String.valueOf(key), value));
        }

        Object toolArgMap = context.getNodeParams().get("tool_arg_map");
        if (toolArgMap instanceof Map<?, ?> rawArgMap) {
            Object specificArgs = rawArgMap.get(toolName);
            if (specificArgs instanceof Map<?, ?> rawSpecificMap) {
                rawSpecificMap.forEach((key, value) -> arguments.putIfAbsent(String.valueOf(key), value));
            }
        }
        return enrichArgumentsForTool(toolName, arguments, resolveUserInput(context));
    }

    private String resolveToolTarget(AgentPromptContext context, String toolName) {
        Object targetMap = context.getNodeParams().get("tool_target_map");
        if (targetMap instanceof Map<?, ?> rawMap) {
            Object target = rawMap.get(toolName);
            if (target != null && !String.valueOf(target).isBlank()) {
                return String.valueOf(target);
            }
        }
        Object directTarget = context.getNodeParams().get(toolName + "_target");
        return directTarget == null ? "" : String.valueOf(directTarget);
    }

    private String resolveUserInput(AgentPromptContext context) {
        Object userMessageRef = context.getNodeParams().get("user_message_ref");
        if (userMessageRef != null) {
            Object resolved = resolveReference(context.getVariables(), String.valueOf(userMessageRef));
            if (resolved != null) {
                return String.valueOf(resolved);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : context.getVariables().entrySet()) {
            Object guideWord = entry.getValue().get("guide_word");
            if (guideWord != null) {
                return String.valueOf(guideWord);
            }
        }
        return "";
    }

    private boolean shouldSelectTool(String toolName, String userInput, List<String> allTools) {
        if (allTools.size() == 1) {
            return true;
        }
        String normalizedToolName = normalizeToolName(toolName);
        if ("query_city_weather".equals(normalizedToolName) || "query_weather".equals(normalizedToolName)) {
            return looksLikeWeatherQuestion(userInput);
        }
        if ("query_air_quality".equals(normalizedToolName)) {
            return looksLikeAirQualityQuestion(userInput) || (looksLikeWeatherQuestion(userInput) && allTools.size() <= 2);
        }
        if ("geocode_city".equals(normalizedToolName)) {
            return looksLikeGeocodeQuestion(userInput);
        }
        if ("calculate_route".equals(normalizedToolName)) {
            return looksLikeRouteQuestion(userInput);
        }
        if ("query_credit_score".equals(normalizedToolName)
                || "query_industry_risk".equals(normalizedToolName)
                || "query_merchant_risk".equals(normalizedToolName)
                || "check_merchant_compliance".equals(normalizedToolName)
                || "load_merchant_profile".equals(normalizedToolName)) {
            return looksLikeMerchantQuestion(userInput);
        }
        return false;
    }

    private boolean looksLikeWeatherQuestion(String userInput) {
        String normalized = lower(userInput);
        return normalized.contains("天气")
                || normalized.contains("气温")
                || normalized.contains("温度")
                || normalized.contains("下雨")
                || normalized.contains("降雨")
                || normalized.contains("weather");
    }

    private boolean looksLikeAirQualityQuestion(String userInput) {
        String normalized = lower(userInput);
        return normalized.contains("空气")
                || normalized.contains("aqi")
                || normalized.contains("pm2.5")
                || normalized.contains("pm10")
                || normalized.contains("污染");
    }

    private boolean looksLikeGeocodeQuestion(String userInput) {
        String normalized = lower(userInput);
        return normalized.contains("坐标")
                || normalized.contains("经纬度")
                || normalized.contains("定位")
                || normalized.contains("在哪")
                || normalized.contains("geocode");
    }

    private boolean looksLikeRouteQuestion(String userInput) {
        String normalized = lower(userInput);
        return normalized.contains("路线")
                || normalized.contains("导航")
                || normalized.contains("怎么走")
                || normalized.contains("怎么去")
                || normalized.contains("路程")
                || normalized.contains("多久")
                || normalized.contains("route")
                || (normalized.contains("从") && normalized.contains("到"));
    }

    private boolean looksLikeMerchantQuestion(String userInput) {
        String normalized = lower(userInput);
        return normalized.contains("商户")
                || normalized.contains("入驻")
                || normalized.contains("预审")
                || normalized.contains("风控")
                || normalized.contains("信用分")
                || normalized.contains("merchant");
    }

    private String guessCityFromText(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "";
        }
        String text = userInput.trim();
        String[] commonCities = {"南京", "北京", "上海", "杭州", "深圳", "广州", "苏州", "成都", "武汉", "西安"};
        for (String city : commonCities) {
            if (text.contains(city)) {
                return city;
            }
        }
        Map<String, String> englishCities = Map.of(
                "nanjing", "Nanjing",
                "beijing", "Beijing",
                "shanghai", "Shanghai",
                "hangzhou", "Hangzhou",
                "shenzhen", "Shenzhen",
                "guangzhou", "Guangzhou",
                "suzhou", "Suzhou",
                "chengdu", "Chengdu",
                "wuhan", "Wuhan",
                "xian", "Xi'an"
        );
        String normalized = lower(text);
        for (Map.Entry<String, String> entry : englishCities.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }

    private Map<String, String> guessRouteFromText(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Map.of();
        }
        String normalized = userInput.replace("，", ",").replace("。", "").trim();
        int fromIndex = normalized.indexOf("从");
        int toIndex = normalized.indexOf("到", Math.max(0, fromIndex + 1));
        if (fromIndex >= 0 && toIndex > fromIndex) {
            String origin = normalized.substring(fromIndex + 1, toIndex).trim();
            String destination = normalized.substring(toIndex + 1)
                    .replace("怎么走", "")
                    .replace("怎么去", "")
                    .replace("路线", "")
                    .replace("导航", "")
                    .replace("多久", "")
                    .replace("需要多久", "")
                    .replace("路程", "")
                    .trim();
            if (!origin.isBlank() && !destination.isBlank()) {
                return Map.of("origin", origin, "destination", destination);
            }
        }
        return Map.of();
    }

    private String guessMerchantId(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "merchant-demo-001";
        }
        String text = userInput.trim();
        String[] candidates = {"merchant-manual-001", "merchant-missing-001", "merchant-medium-001", "merchant-risk-001", "merchant-demo-001"};
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return candidate;
            }
        }
        if (text.contains("人工")) {
            return "merchant-manual-001";
        }
        if (text.contains("缺件") || text.contains("补件")) {
            return "merchant-missing-001";
        }
        if (text.contains("中风险")) {
            return "merchant-medium-001";
        }
        return "merchant-demo-001";
    }

    private Map<String, Object> enrichArgumentsForTool(String toolName, Map<String, Object> arguments, String userInput) {
        Map<String, Object> enriched = new LinkedHashMap<>(arguments);
        String normalizedToolName = normalizeToolName(toolName);
        if (("query_city_weather".equals(normalizedToolName)
                || "query_weather".equals(normalizedToolName)
                || "query_air_quality".equals(normalizedToolName)
                || "geocode_city".equals(normalizedToolName))
                && !enriched.containsKey("city")) {
            String city = guessCityFromText(userInput);
            if (!city.isBlank()) {
                enriched.put("city", city);
            }
        }
        if ("calculate_route".equals(normalizedToolName)
                && (!enriched.containsKey("origin") || !enriched.containsKey("destination"))) {
            Map<String, String> route = guessRouteFromText(userInput);
            route.forEach(enriched::putIfAbsent);
        }
        if (("query_credit_score".equals(normalizedToolName)
                || "query_industry_risk".equals(normalizedToolName)
                || "query_merchant_risk".equals(normalizedToolName)
                || "check_merchant_compliance".equals(normalizedToolName)
                || "load_merchant_profile".equals(normalizedToolName))
                && !enriched.containsKey("merchantId")) {
            enriched.put("merchantId", guessMerchantId(userInput));
        }
        return enriched;
    }

    private Object resolveReference(Map<String, Map<String, Object>> variables, String reference) {
        if (reference == null || reference.isBlank() || variables == null) {
            return null;
        }
        String[] parts = reference.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        Map<String, Object> nodeVariables = variables.get(parts[0]);
        return nodeVariables == null ? null : nodeVariables.get(parts[1]);
    }

    private Map<String, Object> firstToolResult(AgentPromptContext context, String... suffixes) {
        for (String suffix : suffixes) {
            for (Map.Entry<String, Map<String, Object>> entry : context.getToolResults().entrySet()) {
                if (suffix.equals(normalizeToolName(entry.getKey()))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String buildToolResultSummary(AgentPromptContext context) {
        if (context.getToolResults().isEmpty()) {
            return "No tool results are available.";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Map<String, Object>> entry : context.getToolResults().entrySet()) {
            builder.append("- ").append(entry.getKey()).append(": ");
            Object content = entry.getValue().get("content");
            builder.append(content == null ? "No observation" : content).append('\n');
        }
        return builder.toString().trim();
    }

    private JSONObject firstMessage(JSONObject response) {
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject choice = choices.getJSONObject(0);
        return choice == null ? null : choice.getJSONObject("message");
    }

    private String extractAssistantText(JSONObject response) {
        JSONObject message = firstMessage(response);
        if (message == null) {
            return "";
        }
        Object content = message.get("content");
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof JSONArray array) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < array.size(); index++) {
                Object item = array.get(index);
                if (item instanceof JSONObject object && object.getString("text") != null) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(object.getString("text"));
                }
            }
            return builder.toString().trim();
        }
        return "";
    }

    private String extractDeltaText(JSONObject response) {
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject choice = choices.getJSONObject(0);
        if (choice == null) {
            return "";
        }
        JSONObject delta = choice.getJSONObject("delta");
        if (delta == null) {
            JSONObject message = choice.getJSONObject("message");
            return message == null ? "" : extractContentField(message.get("content"));
        }
        return extractContentField(delta.get("content"));
    }

    private String extractContentField(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof JSONArray array) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < array.size(); index++) {
                Object item = array.get(index);
                if (item instanceof JSONObject object && object.getString("text") != null) {
                    builder.append(object.getString("text"));
                }
            }
            return builder.toString();
        }
        return "";
    }

    private String extractError(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            JSONObject error = json.getJSONObject("error");
            if (error != null && error.getString("message") != null) {
                return error.getString("message");
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private String firstMeaningfulError(String preferred, String fallback) {
        String preferredText = preferred == null ? "" : preferred.trim();
        if (!preferredText.isBlank()) {
            return preferredText;
        }
        String fallbackText = fallback == null ? "" : fallback.trim();
        return fallbackText.isBlank() ? "unknown planning error" : fallbackText;
    }

    private boolean hasToolObservations(AgentPromptContext context) {
        return context != null && context.getToolResults() != null && !context.getToolResults().isEmpty();
    }

    private boolean reachedReactIterationLimit(AgentPromptContext context) {
        return isReactLoopEnabled(context)
                && hasToolObservations(context)
                && reactIteration(context) >= reactMaxIterations(context);
    }

    private boolean isReactLoopEnabled(AgentPromptContext context) {
        Object value = context == null ? null : context.getNodeParams().get("react_loop_enabled");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int reactIteration(AgentPromptContext context) {
        return positiveInt(context == null ? null : context.getNodeParams().get("react_iteration"), 1);
    }

    private int reactMaxIterations(AgentPromptContext context) {
        return Math.max(1, positiveInt(context == null ? null : context.getNodeParams().get("react_max_iterations"), 3));
    }

    private int positiveInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private boolean shouldSkipRepeatedToolCall(AgentPromptContext context, String toolName) {
        return isReactLoopEnabled(context)
                && toolName != null
                && hasToolObservations(context)
                && context.getToolResults().containsKey(toolName);
    }

    private String firstAvailableToolName(AgentPromptContext context, List<String> toolNames) {
        for (String toolName : toolNames) {
            if (!shouldSkipRepeatedToolCall(context, toolName)) {
                return toolName;
            }
        }
        return toolNames.isEmpty() ? "" : stringValue(toolNames.get(0), "");
    }

    private List<String> parseToolNames(Object rawValue) {
        List<String> toolNames = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                toolNames.add(String.valueOf(item));
            }
        } else if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                toolNames.add(String.valueOf(item));
            }
        } else if (rawValue instanceof String text && !text.isBlank()) {
            for (String item : text.split(",")) {
                if (!item.isBlank()) {
                    toolNames.add(item.trim());
                }
            }
        }
        return toolNames;
    }

    private Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content == null ? "" : content);
    }

    private String normalizeOpenAiRole(String role) {
        if (role == null) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return switch (normalized) {
            case "assistant", "ai" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private Map<String, Object> defaultToolSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("additionalProperties", true);
        return schema;
    }

    private boolean shouldStream(AgentPromptContext context) {
        return context != null
                && context.getStreamListener() != null
                && !isPlanningStage(context);
    }

    private boolean isPlanningStage(AgentPromptContext context) {
        String stage = String.valueOf(context.getNodeParams().getOrDefault("agent_stage", "chat")).toLowerCase();
        return "plan".equals(stage) || "planner".equals(stage);
    }

    private String resolveChatCompletionUrl() {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v4")) {
            return baseUrl + "/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null) {
            return "";
        }
        int index = toolName.indexOf("__");
        return index >= 0 ? toolName.substring(index + 2) : toolName;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
