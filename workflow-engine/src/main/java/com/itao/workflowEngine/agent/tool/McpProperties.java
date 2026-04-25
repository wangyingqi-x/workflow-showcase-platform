package com.itao.workflowEngine.agent.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    private List<McpServerConfig> servers = new ArrayList<>();

    public List<McpServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<McpServerConfig> servers) {
        this.servers = servers == null ? new ArrayList<>() : new ArrayList<>(servers);
    }

    public static class McpServerConfig {
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
        private List<McpToolConfig> tools = new ArrayList<>();

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

        public List<McpToolConfig> getTools() {
            return tools;
        }

        public void setTools(List<McpToolConfig> tools) {
            this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        }

        public boolean isStdioTransport() {
            if (!isBlank(transport)) {
                return "stdio".equalsIgnoreCase(transport.trim());
            }
            return !isBlank(command);
        }

        public boolean isHttpTransport() {
            return !isStdioTransport();
        }

        public String transportName() {
            return isStdioTransport() ? "stdio" : "http";
        }

        public boolean hasConnectionConfig() {
            return isStdioTransport() ? !isBlank(command) : !isBlank(url);
        }

        public String describeEndpoint() {
            if (isStdioTransport()) {
                String joinedArgs = args == null || args.isEmpty() ? "" : " " + String.join(" ", args);
                return (command == null ? "" : command) + joinedArgs;
            }
            return url;
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public static class McpToolConfig {
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
