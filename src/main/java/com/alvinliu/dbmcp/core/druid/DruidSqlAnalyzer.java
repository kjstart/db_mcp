package com.alvinliu.dbmcp.core.druid;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.*;
import com.alvinliu.dbmcp.core.AnalysisResult;
import com.alvinliu.dbmcp.core.DangerKeywordMatcher;
import com.alvinliu.dbmcp.core.SqlAnalyzer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL analyzer using Alibaba Druid: parse + statement type + whole_text/command_match.
 */
public class DruidSqlAnalyzer implements SqlAnalyzer {

    private final DbType dbType;
    private final List<String> dangerKeywordsWholeText;
    private final List<String> dangerKeywordsAst;

    public DruidSqlAnalyzer(DbType dbType, List<String> dangerKeywordsWholeText,
                            List<String> dangerKeywordsAst) {
        this.dbType = dbType != null ? dbType : DbType.mysql;
        this.dangerKeywordsWholeText = dangerKeywordsWholeText != null ? dangerKeywordsWholeText : Collections.emptyList();
        this.dangerKeywordsAst = dangerKeywordsAst != null ? dangerKeywordsAst : Collections.emptyList();
    }

    @Override
    public AnalysisResult analyze(String sql) {
        AnalysisResult r = new AnalysisResult();
        r.setOriginalSQL(sql);

        if (sql == null || sql.isBlank()) {
            r.setMatchedKeywords(Collections.emptyList());
            r.setMatchedKeywordsForHighlight(Collections.emptyList());
            r.setMatchedActions(Collections.emptyList());
            r.setNormalizedSQL("");
            r.setPreviewSql("");
            r.setParseSucceeded(false);
            r.setMultiStatement(false);
            r.setContainsPLSQL(false);
            r.setPlsqlCreationDDL(false);
            r.setDdl(false);
            r.setStatementType("UNKNOWN");
            r.setDangerous(false);
            return r;
        }

        String trimmed = sql.trim();
        try {
            List<SQLStatement> stmts = SQLUtils.parseStatements(trimmed, dbType);
            if (stmts == null || stmts.isEmpty()) {
                parseFailedRequireReview(r, trimmed);
                return r;
            }
            // 1) whole_text_match on original -> review keywords (trigger review if hit)
            List<String> matchedOnOriginal = new ArrayList<>(DangerKeywordMatcher.matchWholeText(trimmed, dangerKeywordsWholeText));
            matchedOnOriginal.addAll(DangerKeywordMatcher.matchWholeText(trimmed, dangerKeywordsAst));
            dedupeKeywords(matchedOnOriginal);
            // 2) After format: whole_text_match on formatted text again; use for highlight on formatted HTML
            String formattedSql = SQLUtils.toSQLString(stmts, dbType).trim();
            List<String> matchedOnFormatted = new ArrayList<>(DangerKeywordMatcher.matchWholeText(formattedSql, dangerKeywordsWholeText));
            matchedOnFormatted.addAll(DangerKeywordMatcher.matchWholeText(formattedSql, dangerKeywordsAst));
            dedupeKeywords(matchedOnFormatted);
            // 3) Either hit triggers review; merge for dialog display; highlight only on formatted-text hits
            List<String> matchedKeywords = new ArrayList<>(matchedOnOriginal);
            for (String kw : matchedOnFormatted) {
                if (kw != null && !kw.isBlank() && matchedKeywords.stream().noneMatch(k -> k != null && k.trim().equalsIgnoreCase(kw.trim())))
                    matchedKeywords.add(kw);
            }
            dedupeKeywords(matchedKeywords);
            boolean multiStatement = stmts.size() > 1;
            List<String> matchedActions = new ArrayList<>();
            Set<String> actionSeen = new HashSet<>();
            boolean[] ddlRef = new boolean[1];
            boolean[] containsPLSQLRef = new boolean[1];
            String[] firstTypeRef = new String[1];
            for (SQLStatement stmt : stmts) {
                collectStatementTypes(stmt, matchedActions, actionSeen, firstTypeRef, ddlRef, containsPLSQLRef);
            }
            boolean ddl = ddlRef[0];
            boolean containsPLSQL = containsPLSQLRef[0];
            String firstType = firstTypeRef[0];

            r.setMatchedKeywords(matchedKeywords);
            r.setMatchedKeywordsForHighlight(matchedOnFormatted);
            r.setMatchedActions(matchedActions);
            r.setNormalizedSQL(formattedSql);
            r.setPreviewSql(formattedSql);
            r.setParseSucceeded(true);
            r.setMultiStatement(multiStatement);
            r.setContainsPLSQL(containsPLSQL);
            r.setPlsqlCreationDDL(false);
            r.setDdl(ddl);
            r.setStatementType(matchedActions.isEmpty() ? (firstType != null ? firstType : "UNKNOWN") : matchedActions.get(0));
            r.setDangerous(!matchedKeywords.isEmpty() || !matchedActions.isEmpty());
            return r;
        } catch (Exception e) {
            parseFailedRequireReview(r, trimmed);
            return r;
        }
    }

    /** Parse failed: merge command_match keywords into whole_text, run whole_text_match on original; preview shows original. */
    private void parseFailedRequireReview(AnalysisResult r, String trimmed) {
        List<String> merged = new ArrayList<>(dangerKeywordsWholeText);
        merged.addAll(dangerKeywordsAst);
        List<String> onOriginal = new ArrayList<>(DangerKeywordMatcher.matchWholeText(trimmed, merged));
        r.setMatchedKeywords(onOriginal);
        r.setMatchedKeywordsForHighlight(onOriginal); // highlight on preview (original) when parse failed
        r.setMatchedActions(Collections.emptyList());
        r.setNormalizedSQL(trimmed);
        r.setPreviewSql(trimmed);
        r.setParseSucceeded(false);
        r.setMultiStatement(trimmed.contains(";"));
        r.setContainsPLSQL(false);
        r.setPlsqlCreationDDL(false);
        r.setDdl(true);
        r.setStatementType("SQL Error");
        r.setDangerous(true);
    }

    /** Dedupe by case-insensitive key, keep first occurrence. */
    private static void dedupeKeywords(List<String> list) {
        Set<String> seen = new HashSet<>();
        list.removeIf(kw -> {
            if (kw == null || kw.isBlank()) return true;
            if (!seen.add(kw.trim().toLowerCase())) return true;
            return false;
        });
    }

    /** True if statement type matches any command_match keyword. */
    private boolean isCommandMatch(String statementType) {
        if (statementType == null || statementType.isBlank()) return false;
        String stLower = statementType.trim().toLowerCase();
        for (String kw : dangerKeywordsAst) {
            if (kw == null) continue;
            if (kw.trim().toLowerCase().equals(stLower)) return true;
        }
        return false;
    }

    private static boolean isDdl(SQLStatement stmt) {
        if (stmt == null) return false;
        return stmt instanceof SQLDDLStatement
            || stmt instanceof SQLCreateTableStatement
            || stmt instanceof SQLAlterTableStatement
            || stmt instanceof SQLDropTableStatement
            || stmt instanceof SQLCreateViewStatement
            || stmt instanceof SQLDropViewStatement
            || stmt instanceof SQLCreateIndexStatement
            || stmt instanceof SQLDropIndexStatement;
    }

    private static String statementTypeFrom(SQLStatement stmt) {
        if (stmt == null) return "UNKNOWN";
        if (stmt instanceof SQLSelectStatement) return "SELECT";
        if (stmt instanceof SQLInsertStatement) return "INSERT";
        if (stmt instanceof SQLUpdateStatement) return "UPDATE";
        if (stmt instanceof SQLDeleteStatement) return "DELETE";
        if (stmt instanceof SQLTruncateStatement) return "TRUNCATE";
        if (stmt instanceof SQLMergeStatement) return "MERGE";
        if (stmt instanceof SQLDropTableStatement) return "DROP";
        if (stmt instanceof SQLCreateTableStatement) return "CREATE";
        if (stmt instanceof SQLAlterTableStatement) return "ALTER";
        if (stmt instanceof SQLCreateViewStatement || stmt instanceof SQLDropViewStatement) return "DDL";
        if (stmt instanceof SQLCreateIndexStatement || stmt instanceof SQLDropIndexStatement) return "DDL";
        if (stmt instanceof SQLDDLStatement) return "DDL";
        String name = stmt.getClass().getSimpleName();
        if (name.startsWith("SQLCreate")) return "CREATE";
        if (name.startsWith("SQLDrop")) return "DROP";
        if (name.startsWith("SQLAlter")) return "ALTER";
        // Druid Oracle may return OracleCreateProcedureStatement / OracleCreateFunctionStatement etc.
        if (name.contains("CreateProcedure") || name.contains("CreateFunction")) return "CREATE";
        return "UNKNOWN";
    }

    /**
     * Collect statement types recursively: top-level + block inner (e.g. statementList of BEGIN...END).
     * Inner DELETE/UPDATE etc. also participate in command_match; block sets containsPLSQL.
     */
    private void collectStatementTypes(SQLStatement stmt, List<String> matchedActions,
                                       Set<String> actionSeen, String[] firstTypeRef,
                                       boolean[] ddlRef, boolean[] containsPLSQLRef) {
        if (stmt == null) return;
        if (getBlockStatementList(stmt) != null) {
            containsPLSQLRef[0] = true;
            for (SQLStatement inner : getBlockStatementList(stmt)) {
                collectStatementTypes(inner, matchedActions, actionSeen, firstTypeRef, ddlRef, containsPLSQLRef);
            }
        }
        String t = statementTypeFrom(stmt);
        if (firstTypeRef[0] == null && t != null) firstTypeRef[0] = t;
        if (t != null && isCommandMatch(t) && !actionSeen.contains(t.toUpperCase())) {
            actionSeen.add(t.toUpperCase());
            matchedActions.add(t);
        }
        if (isDdl(stmt)) ddlRef[0] = true;
    }

    /** If stmt has getStatementList (e.g. PL/SQL block), return inner list; else null. */
    @SuppressWarnings("unchecked")
    private static List<SQLStatement> getBlockStatementList(SQLStatement stmt) {
        if (stmt == null) return null;
        try {
            Method m = stmt.getClass().getMethod("getStatementList");
            Object list = m.invoke(stmt);
            if (list instanceof List) return (List<SQLStatement>) list;
        } catch (Exception ignored) { }
        return null;
    }

}
