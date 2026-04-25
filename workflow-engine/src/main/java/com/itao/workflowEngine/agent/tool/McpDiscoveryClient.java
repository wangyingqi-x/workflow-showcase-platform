package com.itao.workflowEngine.agent.tool;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class McpDiscoveryClient {

    public DiscoveryResult discover(McpProperties.McpServerConfig server) {
        if (server == null || !server.hasConnectionConfig()) {
            throw new IllegalArgumentException("MCP server connection config is blank.");
        }
        try (McpClientSessions.McpClientSession session = McpClientSessions.openTransient(server)) {
            McpClientSessions.InitializeResult initializeResult = session.initialize();
            List<DiscoveredTool> discoveredTools = new ArrayList<>();
            if (initializeResult.toolDiscoverySupported()) {
                JSONObject listResponse = session.request("tools/list", java.util.Map.of());
                for (McpClientSessions.McpListedTool tool : McpClientSessions.parseTools(listResponse)) {
                    discoveredTools.add(new DiscoveredTool(tool.name(), tool.description(), tool.inputSchema()));
                }
            }
            return new DiscoveryResult(
                    stringValue(server.getName(), "mcp-server"),
                    discoveredTools,
                    server.transportName()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("MCP discovery request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP discovery request interrupted.", ex);
        }
    }

    private String stringValue(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DiscoveryResult(String serverName, List<DiscoveredTool> tools, String sessionId) {
    }

    public record DiscoveredTool(String name, String description, Map<String, Object> inputSchema) {
    }
}
