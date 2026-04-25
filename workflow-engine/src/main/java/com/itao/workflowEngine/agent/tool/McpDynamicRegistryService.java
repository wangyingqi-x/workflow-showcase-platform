package com.itao.workflowEngine.agent.tool;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class McpDynamicRegistryService {

    private final ToolRegistry toolRegistry;
    private final McpProperties mcpProperties;
    private final McpDiscoveryClient discoveryClient;
    private final Map<String, McpProperties.McpServerConfig> runtimeServers = new ConcurrentHashMap<>();
    private final Map<String, ServerRuntimeState> runtimeStates = new ConcurrentHashMap<>();
    private final AtomicBoolean initialRefreshAttempted = new AtomicBoolean(false);

    public McpDynamicRegistryService(ToolRegistry toolRegistry,
                                     McpProperties mcpProperties,
                                     McpDiscoveryClient discoveryClient) {
        this.toolRegistry = toolRegistry;
        this.mcpProperties = mcpProperties;
        this.discoveryClient = discoveryClient;
        registerConfiguredServers();
    }

    public void refreshConfiguredServersIfNeeded() {
        if (initialRefreshAttempted.compareAndSet(false, true)) {
            refreshAllConfiguredServers();
        }
    }

    public synchronized List<Map<String, Object>> refreshAllConfiguredServers() {
        List<Map<String, Object>> refreshed = new ArrayList<>();
        for (McpProperties.McpServerConfig server : snapshotConfiguredServers()) {
            refreshed.add(refreshServerInternal(server, false));
        }
        return refreshed;
    }

    public synchronized Map<String, Object> refreshServer(String serverName) {
        McpProperties.McpServerConfig server = runtimeServers.get(serverName);
        if (server == null) {
            throw new IllegalArgumentException("Unknown MCP server: " + serverName);
        }
        return refreshServerInternal(server, false);
    }

    public synchronized Map<String, Object> importServer(McpProperties.McpServerConfig server) {
        validateServer(server);
        McpProperties.McpServerConfig copy = copyServer(server);
        runtimeServers.put(copy.getName(), copy);
        ensureRuntimeState(copy);
        return refreshServerInternal(copy, true);
    }

    public List<Map<String, Object>> snapshotStatuses() {
        return runtimeStates.values().stream()
                .sorted(Comparator.comparing(ServerRuntimeState::name))
                .map(ServerRuntimeState::toMap)
                .toList();
    }

    private void registerConfiguredServers() {
        if (mcpProperties == null || mcpProperties.getServers() == null) {
            return;
        }
        for (McpProperties.McpServerConfig server : mcpProperties.getServers()) {
            if (server == null || isBlank(server.getName())) {
                continue;
            }
            McpProperties.McpServerConfig copy = copyServer(server);
            runtimeServers.put(copy.getName(), copy);
            ensureRuntimeState(copy);
        }
    }

    private List<McpProperties.McpServerConfig> snapshotConfiguredServers() {
        return new ArrayList<>(runtimeServers.values());
    }

    private ServerRuntimeState ensureRuntimeState(McpProperties.McpServerConfig server) {
        return runtimeStates.computeIfAbsent(server.getName(), key -> ServerRuntimeState.from(server));
    }

    private Map<String, Object> refreshServerInternal(McpProperties.McpServerConfig server, boolean importedAtRuntime) {
        ServerRuntimeState state = ensureRuntimeState(server);
        state.enabled = server.isEnabled();
        state.transport = server.transportName();
        state.url = server.getUrl();
        state.command = server.getCommand();
        state.endpoint = server.describeEndpoint();
        state.configuredTools = toRuntimeToolNames(server.getName(), server.getTools());
        state.lastCheckedAt = Instant.now();
        state.lastError = "";

        if (!server.isEnabled()) {
            state.status = "DISABLED";
            state.source = importedAtRuntime ? "runtime" : "configured";
            return state.toMap();
        }

        try {
            McpDiscoveryClient.DiscoveryResult discoveryResult = discoveryClient.discover(server);
            registerServerTools(server, discoveryResult.tools());
            state.importedTools = discoveryResult.tools().stream()
                    .map(tool -> server.getName() + "__" + tool.name())
                    .toList();
            state.status = "HEALTHY";
            state.source = "discovered";
            state.lastImportedAt = Instant.now();
            return state.toMap();
        } catch (RuntimeException ex) {
            if (!server.getTools().isEmpty()) {
                registerConfiguredFallbackTools(server);
                state.importedTools = toRuntimeToolNames(server.getName(), server.getTools());
                state.status = "DEGRADED";
                state.source = "configured-fallback";
            } else {
                state.importedTools = List.of();
                state.status = "FAILED";
                state.source = importedAtRuntime ? "runtime" : "configured";
            }
            state.lastError = ex.getMessage();
            return state.toMap();
        }
    }

    private void registerServerTools(McpProperties.McpServerConfig server,
                                     List<McpDiscoveryClient.DiscoveredTool> discoveredTools) {
        for (McpDiscoveryClient.DiscoveredTool tool : discoveredTools) {
            String runtimeToolName = server.getName() + "__" + tool.name();
            toolRegistry.register(
                    new ToolDefinition(runtimeToolName, tool.description(), tool.inputSchema()),
                    new McpToolExecutor(server, tool.name())
            );
        }
    }

    private void registerConfiguredFallbackTools(McpProperties.McpServerConfig server) {
        for (McpProperties.McpToolConfig tool : server.getTools()) {
            if (tool == null || isBlank(tool.getName())) {
                continue;
            }
            String runtimeToolName = server.getName() + "__" + tool.getName();
            String description = isBlank(tool.getDescription())
                    ? "MCP tool from " + server.getName() + ": " + tool.getName()
                    : tool.getDescription();
            toolRegistry.register(
                    new ToolDefinition(runtimeToolName, description, tool.getInputSchema()),
                    new McpToolExecutor(server, tool.getName())
            );
        }
    }

    private List<String> toRuntimeToolNames(String serverName, List<McpProperties.McpToolConfig> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (McpProperties.McpToolConfig tool : tools) {
            if (tool != null && !isBlank(tool.getName())) {
                names.add(serverName + "__" + tool.getName());
            }
        }
        return names;
    }

    private void validateServer(McpProperties.McpServerConfig server) {
        if (server == null || isBlank(server.getName()) || !server.hasConnectionConfig()) {
            throw new IllegalArgumentException("MCP server name and connection config are required.");
        }
    }

    private McpProperties.McpServerConfig copyServer(McpProperties.McpServerConfig server) {
        McpProperties.McpServerConfig copy = new McpProperties.McpServerConfig();
        copy.setName(server.getName());
        copy.setTransport(server.getTransport());
        copy.setUrl(server.getUrl());
        copy.setCommand(server.getCommand());
        copy.setArgs(server.getArgs());
        copy.setEnv(server.getEnv());
        copy.setAuthToken(server.getAuthToken());
        copy.setProtocolVersion(server.getProtocolVersion());
        copy.setEnabled(server.isEnabled());
        copy.setHeaders(server.getHeaders());
        List<McpProperties.McpToolConfig> copiedTools = new ArrayList<>();
        if (server.getTools() != null) {
            for (McpProperties.McpToolConfig tool : server.getTools()) {
                if (tool == null) {
                    continue;
                }
                McpProperties.McpToolConfig copiedTool = new McpProperties.McpToolConfig();
                copiedTool.setName(tool.getName());
                copiedTool.setDescription(tool.getDescription());
                copiedTool.setInputSchema(tool.getInputSchema());
                copiedTools.add(copiedTool);
            }
        }
        copy.setTools(copiedTools);
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ServerRuntimeState {
        private final String name;
        private String transport;
        private String url;
        private String command;
        private String endpoint;
        private boolean enabled;
        private String status;
        private String source;
        private String lastError;
        private Instant lastCheckedAt;
        private Instant lastImportedAt;
        private List<String> configuredTools;
        private List<String> importedTools;

        private ServerRuntimeState(String name) {
            this.name = name;
            this.status = "CONFIGURED";
            this.source = "configured";
            this.lastError = "";
            this.configuredTools = List.of();
            this.importedTools = List.of();
        }

        private static ServerRuntimeState from(McpProperties.McpServerConfig server) {
            ServerRuntimeState state = new ServerRuntimeState(server.getName());
            state.transport = server.transportName();
            state.url = server.getUrl();
            state.command = server.getCommand();
            state.endpoint = server.describeEndpoint();
            state.enabled = server.isEnabled();
            return state;
        }

        private String name() {
            return name;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("transport", transport);
            item.put("url", url);
            item.put("command", command);
            item.put("endpoint", endpoint);
            item.put("enabled", enabled);
            item.put("status", status);
            item.put("source", source);
            item.put("lastError", lastError);
            item.put("lastCheckedAt", lastCheckedAt == null ? null : lastCheckedAt.toString());
            item.put("lastImportedAt", lastImportedAt == null ? null : lastImportedAt.toString());
            item.put("configuredTools", new ArrayList<>(configuredTools));
            item.put("importedTools", new ArrayList<>(importedTools));
            return item;
        }
    }
}
