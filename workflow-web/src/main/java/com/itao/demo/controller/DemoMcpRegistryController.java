package com.itao.demo.controller;

import com.itao.workflowEngine.agent.tool.McpDynamicRegistryService;
import com.itao.workflowEngine.agent.tool.McpProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo/mcp/registry")
public class DemoMcpRegistryController {

    private final McpDynamicRegistryService mcpDynamicRegistryService;

    public DemoMcpRegistryController(McpDynamicRegistryService mcpDynamicRegistryService) {
        this.mcpDynamicRegistryService = mcpDynamicRegistryService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(defaultValue = "false") boolean refresh) {
        if (refresh) {
            return Map.of("servers", mcpDynamicRegistryService.refreshAllConfiguredServers());
        }
        mcpDynamicRegistryService.refreshConfiguredServersIfNeeded();
        return Map.of("servers", mcpDynamicRegistryService.snapshotStatuses());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        return Map.of("servers", mcpDynamicRegistryService.refreshAllConfiguredServers());
    }

    @PostMapping("/refresh/{serverName}")
    public Map<String, Object> refreshServer(@PathVariable String serverName) {
        return Map.of("server", mcpDynamicRegistryService.refreshServer(serverName));
    }

    @PostMapping("/import")
    public Map<String, Object> importServer(@RequestBody ImportRequest request) {
        return Map.of("server", mcpDynamicRegistryService.importServer(request.toConfig()));
    }

    public static class ImportRequest {
        private String name;
        private String transport;
        private String url;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private String authToken;
        private String protocolVersion = "2025-03-26";
        private boolean enabled = true;
        private Map<String, String> headers = new LinkedHashMap<>();
        private List<ImportToolRequest> tools = new ArrayList<>();

        public McpProperties.McpServerConfig toConfig() {
            McpProperties.McpServerConfig config = new McpProperties.McpServerConfig();
            config.setName(name);
            config.setTransport(transport);
            config.setUrl(url);
            config.setCommand(command);
            config.setArgs(args);
            config.setEnv(env);
            config.setAuthToken(authToken);
            config.setProtocolVersion(protocolVersion);
            config.setEnabled(enabled);
            config.setHeaders(headers);
            List<McpProperties.McpToolConfig> toolConfigs = new ArrayList<>();
            for (ImportToolRequest tool : tools) {
                if (tool == null) {
                    continue;
                }
                McpProperties.McpToolConfig toolConfig = new McpProperties.McpToolConfig();
                toolConfig.setName(tool.getName());
                toolConfig.setDescription(tool.getDescription());
                toolConfig.setInputSchema(tool.getInputSchema());
                toolConfigs.add(toolConfig);
            }
            config.setTools(toolConfigs);
            return config;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args == null ? new ArrayList<>() : new ArrayList<>(args);
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(env);
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public void setProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }

        public List<ImportToolRequest> getTools() {
            return tools;
        }

        public void setTools(List<ImportToolRequest> tools) {
            this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        }
    }

    public static class ImportToolRequest {
        private String name;
        private String description;
        private Map<String, Object> inputSchema = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputSchema);
        }
    }
}
