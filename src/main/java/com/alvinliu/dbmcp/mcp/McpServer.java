package com.alvinliu.dbmcp.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.alvinliu.dbmcp.audit.Auditor;
import com.alvinliu.dbmcp.config.Config;
import com.alvinliu.dbmcp.confirm.ConfirmRequest;
import com.alvinliu.dbmcp.confirm.Confirmer;
import com.alvinliu.dbmcp.core.AnalysisResult;
import com.alvinliu.dbmcp.core.SqlAnalyzer;
import com.alvinliu.dbmcp.core.SqlFormatter;
import com.alvinliu.dbmcp.jdbc.ExecutionResult;
import com.alvinliu.dbmcp.jdbc.JdbcExecutor;
import com.alvinliu.dbmcp.jdbc.JdbcPool;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.*;

/**
 * MCP server: JSON-RPC 2.0 over stdio. Tools: list_connections, execute_sql, execute_sql_file, query_to_csv_file, query_to_text_file.
 * Supports require_confirm_for_ddl, danger_keywords confirmation, audit log, verbose stderr (same as Go version).
 */
public class McpServer {
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final int ERR_CODE_USER_REJECTED = -32000;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final Config config;
    private final JdbcPool pool;
    private final Auditor auditor;
    private final Confirmer confirmer;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private volatile String lastVerboseMsg;
    private volatile long lastVerboseAt;

    public McpServer(Config config, JdbcPool pool, InputStream in, OutputStream out) {
        this.config = config;
        this.pool = pool;
        this.confirmer = new Confirmer();
        Auditor a = null;
        if (config.getLogging() != null && config.getLogging().isAuditLog()) {
            String logFile = config.getLogging().getLogFile();
            if (logFile == null || logFile.isBlank()) logFile = "audit.log";
            if (config.getConfigPath() != null && !Paths.get(logFile).isAbsolute()) {
                Path configPath = Paths.get(config.getConfigPath());
                if (configPath.getParent() != null) {
                    logFile = configPath.getParent().resolve(logFile).toString();
                }
            }
            try {
                a = new Auditor(logFile);
            } catch (IOException e) {
                System.err.println("[db_mcp] audit log init failed: " + e.getMessage());
            }
        }
        this.auditor = a;
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    public void run() throws IOException {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handleRequest(line);
            }
        } finally {
            pool.close();
            if (auditor != null) {
                try { auditor.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void handleRequest(String line) {
        Map<String, Object> req;
        try {
            req = GSON.fromJson(line, MAP_TYPE);
        } catch (Exception e) {
            sendError(null, -32700, "Parse error", null);
            return;
        }
        String method = (String) req.get("method");
        Object id = req.get("id");
        if ("initialize".equals(method)) {
            handleInitialize(id);
        } else if ("initialized".equals(method) || "notifications/initialized".equals(method)) {
        } else if ("tools/list".equals(method)) {
            handleToolsList(id);
        } else if ("tools/call".equals(method)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) req.get("params");
            handleToolsCall(id, params);
        } else if ("ping".equals(method)) {
            sendResult(id, Map.of("status", "ok"));
        } else {
            if (id != null) {
                sendError(id, -32601, "Method not found: " + method, null);
            }
        }
    }

    private void handleInitialize(Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of(
            "tools", Map.of("listChanged", false),
            "logging", Collections.emptyMap()
        ));
        result.put("serverInfo", Map.of("name", "db-mcp-server", "version", "1.0.0"));
        sendResult(id, result);
    }

    private void handleToolsList(Object id) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool(
            "execute_sql",
            "Execute SQL against the configured database. When multiple connections are configured, use the 'connection' argument (call list_connections to see names). SQL that matches danger_keywords or DDL (if require_confirm_for_ddl) opens a confirmation window.",
            Map.of(
                "sql", prop("string", "SQL to run: one or multiple statements (separated by semicolon)."),
                "connection", prop("string", "Which configured database to use. Required when multiple connections; omit when only one.")
            ),
            List.of("sql")
        ));
        tools.add(tool(
            "execute_sql_file",
            "Read SQL from a file, analyze it (same rules as execute_sql). If review is required (danger_keywords or DDL), a confirmation window shows the formatted file content. On approve, execute the file contents. File path is relative to server working directory unless absolute.",
            Map.of(
                "file_path", prop("string", "Absolute path to the SQL file (callers must use absolute path; relative path depends on server working directory and may fail)."),
                "connection", prop("string", "Which configured database to use. Required when multiple connections; omit when only one.")
            ),
            List.of("file_path")
        ));
        tools.add(tool(
            "list_connections",
            "List configured database connections, their availability, and db_type (mysql/oracle/postgresql/sql_server). Each call re-checks every connection (validates with the database); unavailable or previously failed connections are retried. Use 'name' as the 'connection' argument in execute_sql; use db_type for SQL syntax reference.",
            Map.of(),
            List.of()
        ));
        tools.add(tool(
            "query_to_csv_file",
            "Execute the given SQL and write the result to a file as CSV (header + data rows, UTF-8). Format follows RFC 4180. file_path must be absolute. No confirmation dialog.",
            Map.of(
                "sql", prop("string", "SQL to run (e.g. SELECT). Single or multiple statements; last result is written."),
                "file_path", prop("string", "Absolute path of the output CSV file."),
                "connection", prop("string", "Which configured database to use. Required when multiple connections; omit when only one.")
            ),
            List.of("sql", "file_path")
        ));
        tools.add(tool(
            "query_to_text_file",
            "Execute the given SQL and write the result to a file as plain text: no header, columns tab-separated. No extra newlines added between rows; only newlines in the cell data are written. CLOB columns are read in full. Use for procedure source or any query (including CLOB). file_path must be absolute. No confirmation dialog.",
            Map.of(
                "sql", prop("string", "SQL to run (e.g. SELECT text FROM user_source ...). Single or multiple statements; last result is written."),
                "file_path", prop("string", "Absolute path of the output text file (e.g. .sql)."),
                "connection", prop("string", "Which configured database to use. Required when multiple connections; omit when only one.")
            ),
            List.of("sql", "file_path")
        ));
        sendResult(id, Map.of("tools", tools));
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        t.put("inputSchema", schema);
        return t;
    }

    private Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    @SuppressWarnings("unchecked")
    private void handleToolsCall(Object id, Map<String, Object> params) {
        if (params == null) {
            sendToolError(id, "Missing params");
            return;
        }
        String name = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");
        if (args == null) args = Map.of();
        if ("list_connections".equals(name)) {
            List<Map<String, Object>> connections = pool.listConnectionsWithStatus();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("connections", connections);
            out.put("message", "Use these names as the 'connection' argument in execute_sql. Unavailable connections are retried on each list_connections call.");
            sendToolResult(id, GSON.toJson(out));
        } else if ("execute_sql".equals(name)) {
            handleExecuteSql(id, args);
        } else if ("execute_sql_file".equals(name)) {
            handleExecuteSqlFile(id, args);
        } else if ("query_to_csv_file".equals(name)) {
            handleQueryToCsvFile(id, args);
        } else if ("query_to_text_file".equals(name)) {
            handleQueryToTextFile(id, args);
        } else {
            sendToolError(id, "Unknown tool: " + name);
        }
    }

    /**
     * Remove trailing lines that are only "/" (SQL*Plus execute buffer command). Aligned with Go stripTrailingSlashLine.
     */
    private static String stripTrailingSlashLine(String s) {
        if (s == null) return "";
        while (true) {
            s = s.replaceAll("\\r?\\n$", "");
            int last = s.lastIndexOf('\n');
            if (last < 0) {
                if (s.trim().equals("/")) return "";
                return s;
            }
            String line = s.substring(last + 1);
            if (line.trim().equals("/")) {
                s = s.substring(0, last);
                continue;
            }
            return s;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleExecuteSqlFile(Object id, Map<String, Object> args) {
        Object pathArg = args.get("file_path");
        if (pathArg == null) {
            sendToolError(id, "Missing required parameter: file_path");
            return;
        }
        String filePath = pathArg.toString().trim();
        if (filePath.isEmpty()) {
            sendToolError(id, "file_path cannot be empty");
            return;
        }
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        path = path.normalize();

        String sql;
        try {
            sql = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            sendToolError(id, "Cannot read file: " + e.getMessage());
            return;
        }
        if (sql == null || sql.isBlank()) {
            sendToolError(id, "File is empty");
            return;
        }
        sql = stripTrailingSlashLine(sql).trim();
        if (sql.isEmpty()) {
            sendToolError(id, "File contains no SQL (only \"/\" lines)");
            return;
        }

        String connectionName = args.get("connection") != null ? args.get("connection").toString().trim() : "";
        List<String> names = pool.getNames();
        if (connectionName.isEmpty() && names.size() == 1) {
            connectionName = names.get(0);
        } else if (connectionName.isEmpty() && names.size() > 1) {
            sendToolError(id, "Multiple connections configured; specify 'connection' (call list_connections for names).");
            return;
        }
        String displayConnection = connectionName.isEmpty() ? (names.isEmpty() ? "" : names.get(0)) : connectionName;
        if (displayConnection.isEmpty()) displayConnection = "default";

        String connKey = connectionName.isEmpty() ? names.get(0) : connectionName;
        SqlAnalyzer analyzer = pool.getAnalyzer(connKey);
        AnalysisResult analysis = analyzer.analyze(sql);
        boolean needsConfirmation = analysis.isDangerous()
            || (config.getReview() != null && config.getReview().isAlwaysReviewDdl() && analysis.isDdl());

        String[] meta = pool.getConnectionMeta(connKey);
        String dbName = (meta != null && meta.length > 0) ? meta[0] : "";
        String schema = (meta != null && meta.length > 1) ? meta[1] : "";
        String driver = (meta != null && meta.length > 2) ? meta[2] : "";
        if (dbName.isEmpty()) dbName = displayConnection;

        if (needsConfirmation) {
            ConfirmRequest req = new ConfirmRequest();
            req.setSql(analysis.getPreviewSql() != null ? analysis.getPreviewSql() : sql);
            req.setFormattedHtml(pool.getFormatter(connKey).formatHtmlPreserveLayout(analysis.getPreviewSql() != null ? analysis.getPreviewSql() : sql));
            req.setMatchedKeywords(analysis.getMatchedKeywords());
            req.setMatchedKeywordsForHighlight(analysis.getMatchedKeywordsForHighlight());
            req.setMatchedActions(analysis.getMatchedActions());
            req.setStatementType(analysis.getStatementType());
            req.setDdl(analysis.isDdl());
            req.setConnection(displayConnection);
            req.setSourceLabel("File: " + path);
            req.setDatabaseName(dbName);
            req.setSchema(schema);
            req.setDriver(driver);
            boolean approved;
            try {
                approved = confirmer.confirm(req);
            } catch (Exception e) {
                logAudit(sql, analysis.getMatchedKeywords(), false, "CONFIRM_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver);
                sendToolError(id, "Confirmation dialog error: " + e.getMessage());
                return;
            }
            if (!approved) {
                logAudit(sql, analysis.getMatchedKeywords(), false, "USER_REJECTED", displayConnection, dbName, schema, driver);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("code", "USER_REJECTED");
                data.put("matched_keywords", analysis.getMatchedKeywords() != null ? analysis.getMatchedKeywords() : List.of());
                sendError(id, ERR_CODE_USER_REJECTED, "Execution cancelled by user", data);
                return;
            }
        }

        try (Connection conn = pool.getConnection(connKey)) {
            ExecutionResult result = JdbcExecutor.execute(conn, sql);
            logAudit(sql, analysis.getMatchedKeywords(), true, "SUCCESS", displayConnection, dbName, schema, driver);
            verboseLog("[debug] Execute File Action: " + analysis.getStatementType() + ", Connection: " + displayConnection + ", File: " + path);
            sendToolResult(id, GSON.toJson(result));
        } catch (Exception e) {
            logAudit(sql, analysis.getMatchedKeywords(), false, "EXECUTION_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver);
            if (JdbcPool.isConnectionError(e)) {
                pool.markUnavailable(connKey);
                sendToolError(id, JdbcPool.MSG_CONNECTION_UNAVAILABLE);
            } else {
                sendToolError(id, "SQL execution failed: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleExecuteSql(Object id, Map<String, Object> args) {
        Object sqlArg = args.get("sql");
        if (sqlArg == null) {
            sendToolError(id, "Missing required parameter: sql");
            return;
        }
        String sql = sqlArg.toString().trim();
        String connectionName = args.get("connection") != null ? args.get("connection").toString().trim() : "";
        List<String> names = pool.getNames();
        if (connectionName.isEmpty() && names.size() == 1) {
            connectionName = names.get(0);
        } else if (connectionName.isEmpty() && names.size() > 1) {
            sendToolError(id, "Multiple connections configured; specify 'connection' (call list_connections for names).");
            return;
        }
        String displayConnection = connectionName.isEmpty() ? (names.isEmpty() ? "" : names.get(0)) : connectionName;
        if (displayConnection.isEmpty()) displayConnection = "default";
        String connKey = connectionName.isEmpty() ? names.get(0) : connectionName;

        SqlAnalyzer analyzer = pool.getAnalyzer(connKey);
        AnalysisResult analysis = analyzer.analyze(sql);
        boolean needsConfirmation = analysis.isDangerous()
            || (config.getReview() != null && config.getReview().isAlwaysReviewDdl() && analysis.isDdl());

        String[] meta = pool.getConnectionMeta(connKey);
        String dbName = (meta != null && meta.length > 0) ? meta[0] : "";
        String schema = (meta != null && meta.length > 1) ? meta[1] : "";
        String driver = (meta != null && meta.length > 2) ? meta[2] : "";
        if (dbName.isEmpty()) dbName = displayConnection;

        if (needsConfirmation) {
            String connForFormatter = connKey;
            ConfirmRequest req = new ConfirmRequest();
            req.setSql(analysis.getPreviewSql() != null ? analysis.getPreviewSql() : sql);
            req.setFormattedHtml(pool.getFormatter(connForFormatter).formatHtmlPreserveLayout(analysis.getPreviewSql() != null ? analysis.getPreviewSql() : sql));
            req.setMatchedKeywords(analysis.getMatchedKeywords());
            req.setMatchedKeywordsForHighlight(analysis.getMatchedKeywordsForHighlight());
            req.setMatchedActions(analysis.getMatchedActions());
            req.setStatementType(analysis.getStatementType());
            req.setDdl(analysis.isDdl());
            req.setConnection(displayConnection);
            req.setDatabaseName(dbName);
            req.setSchema(schema);
            req.setDriver(driver);
            boolean approved;
            try {
                approved = confirmer.confirm(req);
            } catch (Exception e) {
                logAudit(sql, analysis.getMatchedKeywords(), false, "CONFIRM_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver);
                sendToolError(id, "Confirmation dialog error: " + e.getMessage());
                return;
            }
            if (!approved) {
                logAudit(sql, analysis.getMatchedKeywords(), false, "USER_REJECTED", displayConnection, dbName, schema, driver);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("code", "USER_REJECTED");
                data.put("matched_keywords", analysis.getMatchedKeywords() != null ? analysis.getMatchedKeywords() : List.of());
                sendError(id, ERR_CODE_USER_REJECTED, "Execution cancelled by user", data);
                return;
            }
        }

        try (Connection conn = pool.getConnection(connKey)) {
            ExecutionResult result = JdbcExecutor.execute(conn, sql);
            logAudit(sql, analysis.getMatchedKeywords(), true, "SUCCESS", displayConnection, dbName, schema, driver);
            verboseLog("[debug] Execute Action: " + analysis.getStatementType() + ", Connection: " + displayConnection);
            sendToolResult(id, GSON.toJson(result));
        } catch (Exception e) {
            logAudit(sql, analysis.getMatchedKeywords(), false, "EXECUTION_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver);
            if (JdbcPool.isConnectionError(e)) {
                pool.markUnavailable(connKey);
                sendToolError(id, JdbcPool.MSG_CONNECTION_UNAVAILABLE);
            } else {
                sendToolError(id, "SQL execution failed: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleQueryToCsvFile(Object id, Map<String, Object> args) {
        Object sqlArg = args.get("sql");
        Object pathArg = args.get("file_path");
        if (sqlArg == null || pathArg == null) {
            sendToolError(id, "Missing required parameter: sql or file_path");
            return;
        }
        String sql = sqlArg.toString().trim();
        if (sql.isEmpty()) {
            sendToolError(id, "sql cannot be empty");
            return;
        }
        Path path = Paths.get(pathArg.toString().trim());
        if (!path.isAbsolute()) {
            sendToolError(id, "file_path must be an absolute path");
            return;
        }
        String connectionName = args.get("connection") != null ? args.get("connection").toString().trim() : "";
        List<String> names = pool.getNames();
        if (connectionName.isEmpty() && names.size() == 1) {
            connectionName = names.get(0);
        } else if (connectionName.isEmpty() && names.size() > 1) {
            sendToolError(id, "Multiple connections configured; specify 'connection' (call list_connections for names).");
            return;
        }
        String connKey = connectionName.isEmpty() ? names.get(0) : connectionName;
        String displayConnection = connectionName.isEmpty() ? (names.isEmpty() ? "" : names.get(0)) : connectionName;
        if (displayConnection.isEmpty()) displayConnection = "default";
        String[] meta = pool.getConnectionMeta(connKey);
        String dbName = (meta != null && meta.length > 0) ? meta[0] : displayConnection;
        String schema = (meta != null && meta.length > 1) ? meta[1] : "";
        String driver = (meta != null && meta.length > 2) ? meta[2] : "";
        try (Connection conn = pool.getConnection(connKey)) {
            long rowsWritten = JdbcExecutor.executeToCsvFile(conn, sql, path);
            logAudit(sql, null, true, "QUERY_TO_CSV", displayConnection, dbName, schema, driver, path.toString());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("file_path", path.toString());
            out.put("rows_written", rowsWritten);
            out.put("message", "CSV written to " + path.toString());
            sendToolResult(id, GSON.toJson(out));
        } catch (Exception e) {
            logAudit(sql, null, false, "QUERY_TO_CSV_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver, path.toString());
            if (JdbcPool.isConnectionError(e)) {
                pool.markUnavailable(connKey);
                sendToolError(id, JdbcPool.MSG_CONNECTION_UNAVAILABLE);
            } else {
                sendToolError(id, "query_to_csv_file failed: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleQueryToTextFile(Object id, Map<String, Object> args) {
        Object sqlArg = args.get("sql");
        Object pathArg = args.get("file_path");
        if (sqlArg == null || pathArg == null) {
            sendToolError(id, "Missing required parameter: sql or file_path");
            return;
        }
        String sql = sqlArg.toString().trim();
        if (sql.isEmpty()) {
            sendToolError(id, "sql cannot be empty");
            return;
        }
        Path path = Paths.get(pathArg.toString().trim());
        if (!path.isAbsolute()) {
            sendToolError(id, "file_path must be an absolute path");
            return;
        }
        String connectionName = args.get("connection") != null ? args.get("connection").toString().trim() : "";
        List<String> names = pool.getNames();
        if (connectionName.isEmpty() && names.size() == 1) {
            connectionName = names.get(0);
        } else if (connectionName.isEmpty() && names.size() > 1) {
            sendToolError(id, "Multiple connections configured; specify 'connection' (call list_connections for names).");
            return;
        }
        String connKey = connectionName.isEmpty() ? names.get(0) : connectionName;
        String displayConnection = connectionName.isEmpty() ? (names.isEmpty() ? "" : names.get(0)) : connectionName;
        if (displayConnection.isEmpty()) displayConnection = "default";
        String[] meta = pool.getConnectionMeta(connKey);
        String dbName = (meta != null && meta.length > 0) ? meta[0] : displayConnection;
        String schema = (meta != null && meta.length > 1) ? meta[1] : "";
        String driver = (meta != null && meta.length > 2) ? meta[2] : "";
        try (Connection conn = pool.getConnection(connKey)) {
            long rowsWritten = JdbcExecutor.executeToTextFile(conn, sql, path);
            logAudit(sql, null, true, "QUERY_TO_TEXT", displayConnection, dbName, schema, driver, path.toString());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("file_path", path.toString());
            out.put("rows_written", rowsWritten);
            out.put("message", "Text written to " + path.toString());
            sendToolResult(id, GSON.toJson(out));
        } catch (Exception e) {
            logAudit(sql, null, false, "QUERY_TO_TEXT_ERROR: " + e.getMessage(), displayConnection, dbName, schema, driver, path.toString());
            if (JdbcPool.isConnectionError(e)) {
                pool.markUnavailable(connKey);
                sendToolError(id, JdbcPool.MSG_CONNECTION_UNAVAILABLE);
            } else {
                sendToolError(id, "query_to_text_file failed: " + e.getMessage());
            }
        }
    }

    private void logAudit(String sql, List<String> keywords, boolean approved, String action,
                          String connection, String dbName, String schema, String driver) {
        logAudit(sql, keywords, approved, action, connection, dbName, schema, driver, null);
    }

    private void logAudit(String sql, List<String> keywords, boolean approved, String action,
                          String connection, String dbName, String schema, String driver, String outputFile) {
        if (auditor != null) {
            auditor.log(sql, keywords, approved, action, connection, dbName, schema, driver, outputFile);
        }
    }

    private void verboseLog(String msg) {
        if (config.getLogging() == null || !config.getLogging().isMcpConsoleLog()) return;
        long now = System.currentTimeMillis();
        if (msg.equals(lastVerboseMsg) && (now - lastVerboseAt) < 2000) return;
        lastVerboseMsg = msg;
        lastVerboseAt = now;
        System.err.println(msg);
    }

    private void sendResult(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        writer.println(GSON.toJson(resp));
    }

    private void sendError(Object id, int code, String message, Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (data != null) err.put("data", data);
        resp.put("error", err);
        writer.println(GSON.toJson(resp));
    }

    private void sendToolResult(Object id, String contentJson) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", contentJson);
        sendResult(id, Map.of("content", List.of(content)));
    }

    private void sendToolError(Object id, String message) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", message);
        sendResult(id, Map.of("content", List.of(content), "isError", true));
    }
}
