package com.itao.workflowEngine.agent.tool;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class DemoToolConfiguration {

    @Bean
    public ToolRegistry toolRegistry(McpProperties mcpProperties) {
        ToolRegistry registry = new ToolRegistry();
        registerMcpTools(registry, mcpProperties);
        return registry;
    }

    private void registerMcpTools(ToolRegistry registry, McpProperties mcpProperties) {
        if (mcpProperties == null || mcpProperties.getServers() == null) {
            return;
        }
        for (McpProperties.McpServerConfig server : mcpProperties.getServers()) {
            if (server == null || !server.isEnabled() || isBlank(server.getName()) || !server.hasConnectionConfig()) {
                continue;
            }
            for (McpProperties.McpToolConfig tool : server.getTools()) {
                if (tool == null || isBlank(tool.getName())) {
                    continue;
                }
                String runtimeToolName = server.getName() + "__" + tool.getName();
                String description = isBlank(tool.getDescription())
                        ? "MCP tool from " + server.getName() + ": " + tool.getName()
                        : tool.getDescription();
                registry.register(
                        new ToolDefinition(runtimeToolName, description, tool.getInputSchema()),
                        new McpToolExecutor(server, tool.getName())
                );
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
