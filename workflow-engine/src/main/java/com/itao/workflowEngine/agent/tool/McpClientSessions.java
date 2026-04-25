package com.itao.workflowEngine.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class McpClientSessions {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Map<String, McpClientSession> SESSION_CACHE = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (McpClientSession session : SESSION_CACHE.values()) {
                try {
                    session.close();
                } catch (Exception ignored) {
                    // Ignore shutdown-time cleanup failures.
                }
            }
        }, "workflow-mcp-session-shutdown"));
    }

    private McpClientSessions() {
    }

    static McpClientSession getCached(McpProperties.McpServerConfig server) {
        return SESSION_CACHE.computeIfAbsent(cacheKey(server), key -> createSession(server));
    }

    static McpClientSession openTransient(McpProperties.McpServerConfig server) {
        return createSession(server);
    }

    static Object buildInitializeParams(String protocolVersion) {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", defaultProtocolVersion(protocolVersion));
        params.put("capabilities", Map.of(
                "tools", Map.of(),
                "sampling", Map.of()
        ));
        params.put("clientInfo", Map.of(
                "name", "workflow-showcase-platform",
                "version", "1.0.0"
        ));
        return params;
    }

    static List<McpListedTool> parseTools(JSONObject responseBody) {
        JSONObject result = responseBody == null ? null : responseBody.getJSONObject("result");
        JSONArray tools = result == null ? null : result.getJSONArray("tools");
        List<McpListedTool> discoveredTools = new ArrayList<>();
        if (tools == null) {
            return discoveredTools;
        }
        for (int index = 0; index < tools.size(); index++) {
            JSONObject item = tools.getJSONObject(index);
            if (item == null || isBlank(item.getString("name"))) {
                continue;
            }
            discoveredTools.add(new McpListedTool(
                    item.getString("name"),
                    stringValue(item.getString("description"), item.getString("name")),
                    extractInputSchema(item)
            ));
        }
        return discoveredTools;
    }

    static boolean isInvalidSession(JSONObject error) {
        if (error == null) {
            return false;
        }
        String message = error.getString("message");
        return message != null && message.toLowerCase().contains("session");
    }

    private static McpClientSession createSession(McpProperties.McpServerConfig server) {
        if (server == null) {
            throw new IllegalArgumentException("MCP server config is required.");
        }
        if (server.isStdioTransport()) {
            return new StdioMcpClientSession(server);
        }
        return new HttpMcpClientSession(server);
    }

    private static String cacheKey(McpProperties.McpServerConfig server) {
        StringBuilder builder = new StringBuilder();
        builder.append(server.transportName()).append('|')
                .append(stringValue(server.getName(), "")).append('|')
                .append(stringValue(server.getProtocolVersion(), defaultProtocolVersion(null))).append('|');
        if (server.isStdioTransport()) {
            builder.append(stringValue(server.getCommand(), ""));
            builder.append('|').append(String.join("\u001F", server.getArgs() == null ? List.of() : server.getArgs()));
            builder.append('|').append(JSON.toJSONString(server.getEnv() == null ? Map.of() : new LinkedHashMap<>(server.getEnv())));
        } else {
            builder.append(stringValue(server.getUrl(), ""));
            builder.append('|').append(stringValue(server.getAuthToken(), ""));
            builder.append('|').append(JSON.toJSONString(server.getHeaders() == null ? Map.of() : new LinkedHashMap<>(server.getHeaders())));
        }
        return builder.toString();
    }

    private static Map<String, Object> extractInputSchema(JSONObject item) {
        JSONObject inputSchema = item.getJSONObject("inputSchema");
        if (inputSchema == null) {
            inputSchema = item.getJSONObject("input-schema");
        }
        if (inputSchema == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");
            fallback.put("properties", new LinkedHashMap<>());
            fallback.put("additionalProperties", true);
            return fallback;
        }
        return new LinkedHashMap<>(inputSchema);
    }

    private static String defaultProtocolVersion(String protocolVersion) {
        return isBlank(protocolVersion) ? "2025-03-26" : protocolVersion;
    }

    private static String stringValue(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record McpListedTool(String name, String description, Map<String, Object> inputSchema) {
    }

    interface McpClientSession extends AutoCloseable {

        InitializeResult initialize() throws IOException, InterruptedException;

        JSONObject request(String method, Object params) throws IOException, InterruptedException;

        void reset();

        @Override
        void close();
    }

    record InitializeResult(boolean toolDiscoverySupported) {
    }

    private abstract static class AbstractMcpClientSession implements McpClientSession {
        protected final String serverName;
        protected final String protocolVersion;
        protected final Object lock = new Object();
        protected boolean initialized;
        protected boolean toolDiscoverySupported;

        protected AbstractMcpClientSession(McpProperties.McpServerConfig server) {
            this.serverName = stringValue(server.getName(), "mcp-server");
            this.protocolVersion = defaultProtocolVersion(server.getProtocolVersion());
        }

        @Override
        public InitializeResult initialize() throws IOException, InterruptedException {
            synchronized (lock) {
                if (initialized) {
                    return new InitializeResult(toolDiscoverySupported);
                }
                JSONObject initializeResponse = sendRequestLocked("initialize", buildInitializeParams(protocolVersion), true);
                JSONObject initializeResult = initializeResponse.getJSONObject("result");
                JSONObject capabilities = initializeResult == null ? null : initializeResult.getJSONObject("capabilities");
                toolDiscoverySupported = capabilities != null && capabilities.containsKey("tools");
                initialized = true;
                sendRequestLocked("notifications/initialized", Map.of(), false);
                return new InitializeResult(toolDiscoverySupported);
            }
        }

        @Override
        public JSONObject request(String method, Object params) throws IOException, InterruptedException {
            synchronized (lock) {
                return sendRequestLocked(method, params, true);
            }
        }

        @Override
        public void reset() {
            synchronized (lock) {
                initialized = false;
                toolDiscoverySupported = false;
                resetLocked();
            }
        }

        @Override
        public void close() {
            reset();
        }

        protected abstract JSONObject sendRequestLocked(String method,
                                                        Object params,
                                                        boolean expectResponse) throws IOException, InterruptedException;

        protected abstract void resetLocked();
    }

    private static final class HttpMcpClientSession extends AbstractMcpClientSession {
        private final String serverUrl;
        private final String authToken;
        private final Map<String, String> extraHeaders;
        private String sessionId;

        private HttpMcpClientSession(McpProperties.McpServerConfig server) {
            super(server);
            this.serverUrl = server.getUrl();
            this.authToken = server.getAuthToken();
            this.extraHeaders = server.getHeaders() == null ? Map.of() : new LinkedHashMap<>(server.getHeaders());
            this.sessionId = "";
        }

        @Override
        protected JSONObject sendRequestLocked(String method,
                                               Object params,
                                               boolean expectResponse) throws IOException, InterruptedException {
            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            if (expectResponse) {
                payload.put("id", UUID.randomUUID().toString());
            }
            payload.put("method", method);
            payload.put("params", params == null ? Map.of() : params);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("MCP-Protocol-Version", protocolVersion)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()));

            if (!isBlank(authToken)) {
                builder.header("Authorization", "Bearer " + authToken);
            }
            for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                if (!isBlank(header.getKey()) && !isBlank(header.getValue())) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
            if (!isBlank(sessionId)) {
                builder.header("Mcp-Session-Id", sessionId);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " calling " + method + " on " + serverName);
            }
            sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(sessionId);
            if (!expectResponse || isBlank(response.body())) {
                return new JSONObject();
            }
            JSONObject body = JSON.parseObject(response.body());
            return body == null ? new JSONObject() : body;
        }

        @Override
        protected void resetLocked() {
            sessionId = "";
        }
    }

    private static final class StdioMcpClientSession extends AbstractMcpClientSession {
        private final String command;
        private final List<String> args;
        private final Map<String, String> env;
        private final Queue<String> recentStderr = new ArrayDeque<>();
        private Process process;
        private BufferedWriter writer;
        private BufferedReader reader;
        private Thread stderrDrainer;

        private StdioMcpClientSession(McpProperties.McpServerConfig server) {
            super(server);
            this.command = server.getCommand();
            this.args = server.getArgs() == null ? List.of() : new ArrayList<>(server.getArgs());
            this.env = server.getEnv() == null ? Map.of() : new LinkedHashMap<>(server.getEnv());
        }

        @Override
        protected JSONObject sendRequestLocked(String method,
                                               Object params,
                                               boolean expectResponse) throws IOException, InterruptedException {
            ensureProcessRunningLocked();

            String requestId = expectResponse ? UUID.randomUUID().toString() : null;
            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            if (requestId != null) {
                payload.put("id", requestId);
            }
            payload.put("method", method);
            payload.put("params", params == null ? Map.of() : params);

            writer.write(payload.toJSONString());
            writer.write('\n');
            writer.flush();

            if (!expectResponse) {
                return new JSONObject();
            }

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IllegalStateException("STDIO MCP process exited while waiting for " + method + " on "
                            + serverName + ". stderr: " + recentStderrSummary());
                }
                if (line.isBlank()) {
                    continue;
                }
                JSONObject body;
                try {
                    body = JSON.parseObject(line);
                } catch (Exception ex) {
                    throw new IllegalStateException("STDIO MCP server " + serverName + " returned non-JSON output: "
                            + abbreviate(line) + ". stderr: " + recentStderrSummary(), ex);
                }
                String responseId = body.getString("id");
                if (requestId.equals(responseId)) {
                    return body;
                }
            }
        }

        @Override
        protected void resetLocked() {
            if (process != null) {
                process.destroy();
                try {
                    process.waitFor();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
            process = null;
            writer = null;
            reader = null;
            stderrDrainer = null;
            recentStderr.clear();
        }

        private void ensureProcessRunningLocked() throws IOException {
            if (process != null && process.isAlive() && writer != null && reader != null) {
                return;
            }
            resetLocked();

            List<String> commandLine = new ArrayList<>();
            commandLine.add(command);
            commandLine.addAll(args);

            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.environment().putAll(env);
            commandLine.set(0, resolveExecutable(builder.environment()));
            builder.command(commandLine);
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            Process startedProcess = process;

            stderrDrainer = new Thread(() -> {
                try (BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(startedProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        synchronized (recentStderr) {
                            if (recentStderr.size() >= 20) {
                                recentStderr.poll();
                            }
                            recentStderr.offer(line);
                        }
                    }
                } catch (IOException ignored) {
                    // Ignore stderr drain failures; request path will surface process errors.
                }
            }, "workflow-mcp-stderr-" + serverName);
            stderrDrainer.setDaemon(true);
            stderrDrainer.start();
        }

        private String resolveExecutable(Map<String, String> processEnvironment) {
            if (isBlank(command)) {
                return command;
            }
            File explicit = new File(command);
            if (explicit.isAbsolute() || command.contains("\\") || command.contains("/")) {
                return command;
            }

            List<String> candidateNames = new ArrayList<>();
            if (isWindows() && !command.contains(".")) {
                String pathExt = processEnvironment.getOrDefault("PATHEXT", ".COM;.EXE;.BAT;.CMD");
                for (String extension : pathExt.split(";")) {
                    if (!isBlank(extension)) {
                        candidateNames.add(command + extension);
                    }
                }
            } else {
                candidateNames.add(command);
            }

            String pathValue = processEnvironment.getOrDefault("PATH", System.getenv("PATH"));
            if (isBlank(pathValue)) {
                return command;
            }
            for (String directory : pathValue.split(File.pathSeparator)) {
                if (isBlank(directory)) {
                    continue;
                }
                for (String candidateName : candidateNames) {
                    File candidate = new File(directory, candidateName);
                    if (candidate.isFile()) {
                        return candidate.getAbsolutePath();
                    }
                }
            }
            return command;
        }

        private boolean isWindows() {
            return File.pathSeparatorChar == ';';
        }

        private String recentStderrSummary() {
            synchronized (recentStderr) {
                return recentStderr.isEmpty() ? "<empty>" : String.join(" | ", recentStderr);
            }
        }

        private String abbreviate(String line) {
            return line.length() <= 240 ? line : line.substring(0, 240) + "...";
        }
    }
}
