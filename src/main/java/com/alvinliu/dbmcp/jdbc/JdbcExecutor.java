package com.alvinliu.dbmcp.jdbc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Execute SQL via JDBC and return ExecutionResult. Splits by semicolon for multiple statements.
 */
public final class JdbcExecutor {

    public static ExecutionResult execute(Connection conn, String sql) {
        ExecutionResult result = new ExecutionResult();
        long start = System.currentTimeMillis();
        sql = sql.trim();
        if (sql.isEmpty()) {
            result.setSuccess(false);
            result.setStatementType("UNKNOWN");
            result.setWarning("empty SQL");
            result.setExecutionTimeMs(System.currentTimeMillis() - start);
            return result;
        }
        // Order: (1) "/" lines split like SQL*Plus/SQLcl, (2) single PL/SQL block, (3) ";" split
        sql = stripTrailingSlashLine(sql).trim();
        List<String> statements;
        if (hasStandaloneSlashLine(sql)) {
            statements = new ArrayList<>();
            for (String chunk : splitBySlashLines(sql)) {
                statements.addAll(expandStatementSegment(conn, chunk));
            }
        } else {
            statements = expandStatementSegment(conn, sql);
        }
        ExecutionResult last = null;
        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) continue;
            last = executeOne(conn, stmt);
            last.setExecutionTimeMs(System.currentTimeMillis() - start);
        }
        if (last != null) {
            result.setColumns(last.getColumns());
            result.setRows(last.getRows());
            result.setRowsAffected(last.getRowsAffected());
            result.setSuccess(last.isSuccess());
            result.setStatementType(last.getStatementType());
            result.setWarning(last.getWarning());
            result.setExecutionTimeMs(last.getExecutionTimeMs());
        } else {
            result.setSuccess(true);
            result.setStatementType(inferStatementType(sql));
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    /** True if the connection is to an Oracle database. */
    private static boolean isOracle(Connection conn) {
        try {
            String product = conn.getMetaData() != null ? conn.getMetaData().getDatabaseProductName() : "";
            return product != null && product.toUpperCase().contains("ORACLE");
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * True if SQL is an Oracle PL/SQL anonymous block (BEGIN...END or DECLARE...BEGIN...END).
     * Leading blank lines and line-comment-only lines are stripped before checking.
     */
    private static boolean isOracleAnonymousBlock(String sql) {
        if (sql == null) return false;
        String trimmed = trimLeadingLineComments(sql).trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String lower = trimmed.toLowerCase();
        boolean hasEnd = lower.contains(" end ") || lower.endsWith(" end") || looksLikePlsqlBlockEnd(lower);
        return (lower.startsWith("begin") || lower.startsWith("declare")) && hasEnd;
    }

    /**
     * Remove leading blank lines and lines that are only "--" line comments.
     */
    private static String trimLeadingLineComments(String sql) {
        if (sql == null) return "";
        String s = sql.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] lines = s.split("\n", -1);
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty() || t.startsWith("--")) {
                start = i + 1;
            } else {
                break;
            }
        }
        if (start >= lines.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (i > start) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    /**
     * True when the last non-empty line is the block-closing END or END label,
     * but not END IF / END LOOP / END CASE.
     */
    private static boolean looksLikePlsqlBlockEnd(String lower) {
        if (lower == null || lower.isEmpty()) return false;
        String[] lines = lower.split("\n", -1);
        String last = "";
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (!t.isEmpty()) { last = t; break; }
        }
        if (last.isEmpty()) return false;
        if (last.equals("end")) return true;
        if (!last.startsWith("end ")) return false;
        String rest = last.substring(4).trim();
        if (rest.isEmpty()) return true;
        String first = rest.split("\\s+")[0];
        switch (first) {
            case "if": case "loop": case "case": return false;
            default: return true; // END some_label
        }
    }

    /** True if any line in the SQL is only "/" (SQL*Plus run buffer command). */
    private static boolean hasStandaloneSlashLine(String sql) {
        if (sql == null) return false;
        for (String line : sql.split("\n", -1)) {
            if (line.trim().equals("/")) return true;
        }
        return false;
    }

    /** Split a script on standalone "/" lines (SQL*Plus run buffer). Empty segments are skipped. */
    private static List<String> splitBySlashLines(String sql) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean firstLine = true;
        for (String line : sql.split("\n", -1)) {
            if (line.trim().equals("/")) {
                String p = current.toString().trim();
                if (!p.isEmpty()) parts.add(p);
                current.setLength(0);
                firstLine = true;
                continue;
            }
            if (!firstLine) current.append('\n');
            current.append(line);
            firstLine = false;
        }
        String p = current.toString().trim();
        if (!p.isEmpty()) parts.add(p);
        return parts;
    }

    /** Expand one script segment (between "/" lines) into executable statements. */
    private static List<String> expandStatementSegment(Connection conn, String sql) {
        if (sql == null || sql.trim().isEmpty()) return List.of();
        if (hasStandaloneSlashLine(sql)) {
            List<String> out = new ArrayList<>();
            for (String chunk : splitBySlashLines(sql)) {
                out.addAll(expandStatementSegment(conn, chunk));
            }
            return out;
        }
        if (isPlsqlDdl(sql)) return List.of(sql);
        if (isOracle(conn) && isOracleAnonymousBlock(sql)) return List.of(sql);
        List<String> result = new ArrayList<>();
        for (String s : splitStatements(sql)) {
            if (!s.trim().isEmpty()) result.add(s);
        }
        return result;
    }

    /** Remove trailing lines that are only "/" (SQL*Plus execute buffer command). */
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

    /** True if SQL is PL/SQL DDL (CREATE FUNCTION/PROCEDURE/PACKAGE) and must be run as one statement. */
    private static boolean isPlsqlDdl(String sql) {
        String u = sql.trim().toUpperCase();
        if (!u.startsWith("CREATE")) return false;
        return u.contains(" FUNCTION ") || u.contains(" PROCEDURE ") || u.contains(" PACKAGE ");
    }

    /**
     * Split SQL by semicolon, but do not split on semicolons inside single-quoted strings
     * (so PL/SQL blocks and DDL like CREATE FUNCTION work as one statement).
     */
    private static String[] splitStatements(String sql) {
        List<String> list = new ArrayList<>();
        int start = 0;
        int len = sql.length();
        int i = 0;
        while (start < len) {
            boolean inSingle = false;
            int semi = -1;
            for (i = start; i < len; i++) {
                char c = sql.charAt(i);
                if (c == '\'') {
                    if (inSingle && i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i++; // skip escaped quote
                    } else {
                        inSingle = !inSingle;
                    }
                } else if (!inSingle && c == ';') {
                    semi = i;
                    break;
                }
            }
            if (semi < 0) {
                list.add(sql.substring(start).trim());
                break;
            }
            list.add(sql.substring(start, semi).trim());
            start = semi + 1;
        }
        return list.toArray(new String[0]);
    }

    private static ExecutionResult executeOne(Connection conn, String sql) {
        ExecutionResult r = new ExecutionResult();
        r.setStatementType(inferStatementType(sql));
        String trimmed = sql != null ? sql.trim() : "";
        try {
            if (isCallable(trimmed)) {
                executeCallable(conn, trimmed, r);
            } else if (isOracle(conn) && isOracleAnonymousBlock(trimmed)) {
                executeOracleAnonymousBlock(conn, trimmed, r);
            } else {
                try (Statement st = conn.createStatement()) {
                    st.setQueryTimeout(300);
                    boolean isResultSet = st.execute(sql);
                    if (isResultSet) {
                        try (ResultSet rs = st.getResultSet()) {
                            ResultSetMetaData meta = rs.getMetaData();
                            int cols = meta.getColumnCount();
                            List<String> columnNames = new ArrayList<>();
                            for (int i = 1; i <= cols; i++) {
                                columnNames.add(meta.getColumnLabel(i));
                            }
                            r.setColumns(columnNames);
                            List<List<Object>> rows = new ArrayList<>();
                            while (rs.next()) {
                                List<Object> row = new ArrayList<>();
                                for (int i = 1; i <= cols; i++) {
                                    Object v = rs.getObject(i);
                                    row.add(v instanceof Clob ? clobToString((Clob) v) : v);
                                }
                                rows.add(row);
                            }
                            r.setRows(rows);
                            r.setRowsAffected(rows.size());
                        }
                    } else {
                        r.setRowsAffected(st.getUpdateCount() >= 0 ? st.getUpdateCount() : 0);
                    }
                    r.setSuccess(true);
                }
            }
        } catch (SQLException e) {
            r.setSuccess(false);
            r.setWarning(e.getMessage());
        }
        return r;
    }

    /**
     * True if SQL uses JDBC call escape syntax for stored procedures/functions, e.g.
     * "{ call proc_name() }" or "{ ? = call func_name(?) }".
     * Supported broadly across Oracle, MySQL, PostgreSQL, SQL Server, etc.
     */
    private static boolean isCallable(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false;
        String upper = trimmed.toUpperCase();
        return upper.contains("CALL");
    }

    /**
     * Execute a JDBC callable statement and populate the given result.
     * This path is used for vendor-neutral stored procedure/function calls.
     */
    private static void executeCallable(Connection conn, String sql, ExecutionResult r) throws SQLException {
        r.setStatementType("CALL");
        try (CallableStatement cs = conn.prepareCall(sql)) {
            cs.setQueryTimeout(300);
            boolean isResultSet = cs.execute();
            if (isResultSet) {
                try (ResultSet rs = cs.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        columnNames.add(meta.getColumnLabel(i));
                    }
                    r.setColumns(columnNames);
                    List<List<Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            Object v = rs.getObject(i);
                            row.add(v instanceof Clob ? clobToString((Clob) v) : v);
                        }
                        rows.add(row);
                    }
                    r.setRows(rows);
                    r.setRowsAffected(rows.size());
                }
            } else {
                r.setRowsAffected(cs.getUpdateCount() >= 0 ? cs.getUpdateCount() : 0);
            }
            r.setSuccess(true);
        }
    }

    /**
     * Execute an Oracle PL/SQL anonymous block via CallableStatement.
     * Only used when db is Oracle and SQL starts with BEGIN or DECLARE.
     */
    private static void executeOracleAnonymousBlock(Connection conn, String sql, ExecutionResult r) throws SQLException {
        r.setStatementType("PLSQL_BLOCK");
        try (CallableStatement cs = conn.prepareCall(sql)) {
            cs.setQueryTimeout(300);
            boolean isResultSet = cs.execute();
            if (isResultSet) {
                try (ResultSet rs = cs.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        columnNames.add(meta.getColumnLabel(i));
                    }
                    r.setColumns(columnNames);
                    List<List<Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            Object v = rs.getObject(i);
                            row.add(v instanceof Clob ? clobToString((Clob) v) : v);
                        }
                        rows.add(row);
                    }
                    r.setRows(rows);
                    r.setRowsAffected(rows.size());
                }
            } else {
                r.setRowsAffected(cs.getUpdateCount() >= 0 ? cs.getUpdateCount() : 0);
            }
            r.setSuccess(true);
        }
    }

    private static String clobToString(Clob clob) throws SQLException {
        if (clob == null) return null;
        try (Reader r = clob.getCharacterStream()) {
            if (r == null) return null;
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            return sb.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to read CLOB", e);
        }
    }

    /**
     * Execute SQL and write the result to a file as CSV (header + rows). Uses UTF-8.
     * Returns the number of rows written. For non–result-set statements writes "Rows affected: N".
     */
    public static long executeToCsvFile(Connection conn, String sql, Path filePath) throws SQLException, IOException {
        ExecutionResult r = execute(conn, sql);
        if (!r.isSuccess()) {
            throw new SQLException(r.getWarning() != null ? r.getWarning() : "Execution failed");
        }
        long rowsWritten;
        try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (r.getColumns() != null && r.getRows() != null) {
                w.write(csvEscapeRow(r.getColumns()));
                w.newLine();
                for (List<Object> row : r.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (Object o : row) cells.add(o == null ? "" : o.toString());
                    w.write(csvEscapeRow(cells));
                    w.newLine();
                }
                rowsWritten = r.getRows().size();
            } else {
                w.write("Rows affected: " + r.getRowsAffected());
                w.newLine();
                rowsWritten = r.getRowsAffected();
            }
        }
        return rowsWritten;
    }

    /**
     * Execute SQL and write the result to a file as plain text: no header, columns tab-separated per row.
     * No extra newlines added between rows; only newlines present in the cell data are written (e.g. CLOB with line breaks).
     * CLOB columns are read in full and written as text. Uses UTF-8. Returns the number of rows written.
     */
    public static long executeToTextFile(Connection conn, String sql, Path filePath) throws SQLException, IOException {
        ExecutionResult r = execute(conn, sql);
        if (!r.isSuccess()) {
            throw new SQLException(r.getWarning() != null ? r.getWarning() : "Execution failed");
        }
        long rowsWritten;
        try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (r.getColumns() != null && r.getRows() != null) {
                for (List<Object> row : r.getRows()) {
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) w.write('\t');
                        Object o = row.get(i);
                        if (o != null) w.write(o.toString());
                    }
                    // do not add newline between rows; only data's own newlines appear
                }
                rowsWritten = r.getRows().size();
            } else {
                w.write("Rows affected: " + r.getRowsAffected());
                w.newLine();
                rowsWritten = r.getRowsAffected();
            }
        }
        return rowsWritten;
    }

    /** CSV standard: quote field if it contains comma, double-quote, newline, or CR; escape " as "". */
    private static String csvEscapeRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String s = cells.get(i) == null ? "" : cells.get(i);
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                sb.append('"').append(s.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(s);
            }
        }
        return sb.toString();
    }

    private static String inferStatementType(String sql) {
        if (sql == null) return "UNKNOWN";
        String upper = sql.toUpperCase().trim();
        if (isCallable(upper)) return "CALL";
        if (isOracleAnonymousBlock(upper)) return "PLSQL_BLOCK";
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("MERGE")) return "MERGE";
        if (upper.startsWith("CREATE")) return "DDL";
        if (upper.startsWith("ALTER")) return "DDL";
        if (upper.startsWith("DROP")) return "DDL";
        if (upper.startsWith("TRUNCATE")) return "DDL";
        return "UNKNOWN";
    }
}
