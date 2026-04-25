package com.itao.workflowEngine.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.itao.workflowEngine.agent.model.ToolExecutionResult;
import com.itao.workflowEngine.graph.GraphState;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class McpToolExecutor implements ToolExecutor {

    private final McpProperties.McpServerConfig serverConfig;
    private final String serverName;
    private final String toolName;

    public McpToolExecutor(McpProperties.McpServerConfig serverConfig, String toolName) {
        this.serverConfig = copyServer(serverConfig);
        this.serverName = this.serverConfig.getName();
        this.toolName = toolName;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, GraphState graphState) {
        ToolExecutionResult result = new ToolExecutionResult();
        if (serverConfig == null || !serverConfig.hasConnectionConfig()) {
            result.setSuccess(false);
            result.setErrorMessage("MCP server connection config is blank.");
            result.setContent("MCP server is not configured.");
            return result;
        }

        McpClientSessions.McpClientSession session = McpClientSessions.getCached(serverConfig);
        try {
            session.initialize();
            JSONObject json = invokeTool(arguments, session, true);
            return parseToolResult(json);
        } catch (IOException ex) {
            session.reset();
            return failure("MCP request failed: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure("MCP request interrupted.");
        } catch (Exception ex) {
            return failure("MCP response parse failed: " + ex.getMessage());
        }
    }

    private JSONObject invokeTool(Map<String, Object> arguments,
                                  McpClientSessions.McpClientSession session,
                                  boolean allowRetry) throws IOException, InterruptedException {
        JSONObject response = session.request("tools/call", Map.of(
                "name", toolName,
                "arguments", arguments == null ? Map.of() : arguments
        ));
        JSONObject error = response.getJSONObject("error");
        if (allowRetry && McpClientSessions.isInvalidSession(error)) {
            session.reset();
            session.initialize();
            return invokeTool(arguments, session, false);
        }
        return response;
    }

    private ToolExecutionResult parseToolResult(JSONObject json) {
        JSONObject error = json.getJSONObject("error");
        if (error != null) {
            ToolExecutionResult result = new ToolExecutionResult();
            result.setSuccess(false);
            result.setErrorMessage(error.getString("message"));
            result.setContent("MCP tool returned an error: " + error.getString("message"));
            result.setStructuredData(Map.of(
                    "server", serverName,
                    "tool", toolName,
                    "error", error
            ));
            return result;
        }

        JSONObject callResult = json.getJSONObject("result");
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(callResult == null || !Boolean.TRUE.equals(callResult.getBoolean("isError")));
        result.setContent(extractText(callResult));
        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("server", serverName);
        structuredData.put("tool", toolName);
        structuredData.put("transport", serverConfig.transportName());
        structuredData.put("endpoint", serverConfig.describeEndpoint());
        structuredData.put("raw", callResult == null ? Map.of() : callResult);
        result.setStructuredData(structuredData);
        return result;
    }

    private ToolExecutionResult failure(String message) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(message);
        result.setContent(message);
        return result;
    }

    private String extractText(JSONObject callResult) {
        if (callResult == null) {
            return "MCP tool returned no content.";
        }
        JSONArray content = callResult.getJSONArray("content");
        if (content == null || content.isEmpty()) {
            Object structuredContent = callResult.get("structuredContent");
            return structuredContent == null ? "MCP tool returned no text content." : JSON.toJSONString(structuredContent);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JSONObject item = content.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = item.getString("text");
            if (text != null && !text.isBlank()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text);
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(JSON.toJSONString(item));
        }
        return builder.length() == 0 ? "MCP tool returned structured content only." : builder.toString();
    }

    private McpProperties.McpServerConfig copyServer(McpProperties.McpServerConfig source) {
        McpProperties.McpServerConfig copy = new McpProperties.McpServerConfig();
        if (source == null) {
            return copy;
        }
        copy.setName(source.getName());
        copy.setTransport(source.getTransport());
        copy.setUrl(source.getUrl());
        copy.setCommand(source.getCommand());
        copy.setArgs(source.getArgs());
        copy.setEnv(source.getEnv());
        copy.setAuthToken(source.getAuthToken());
        copy.setProtocolVersion(source.getProtocolVersion());
        copy.setEnabled(source.isEnabled());
        copy.setHeaders(source.getHeaders());
        copy.setTools(source.getTools());
        return copy;
    }
}
