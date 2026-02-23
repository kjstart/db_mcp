package com.alvinliu.dbmcp.core;

import java.util.List;

/**
 * Result of SQL analysis. matchedKeywords = whole_text_match hits; matchedActions = command_match hits (statement types).
 */
public class AnalysisResult {
    private String originalSQL;
    private String normalizedSQL;
    private List<String> tokens;
    private List<String> matchedKeywords;   // whole_text_match: union of hits on original + formatted (for review trigger and dialog)
    private List<String> matchedKeywordsForHighlight; // whole_text_match: hits on formatted text only (for highlighting on formatted HTML)
    private List<String> matchedActions;    // command_match: statement types that matched (e.g. DELETE, UPDATE)
    private boolean dangerous;
    private boolean ddl;
    private boolean multiStatement;
    private boolean containsPLSQL;
    private boolean plsqlCreationDDL;
    private String statementType;
    /** SQL to show in preview: Druid formatted when parse succeeded, original when failed. */
    private String previewSql;
    private boolean parseSucceeded;

    public String getOriginalSQL() { return originalSQL; }
    public void setOriginalSQL(String originalSQL) { this.originalSQL = originalSQL; }

    public String getNormalizedSQL() { return normalizedSQL; }
    public void setNormalizedSQL(String normalizedSQL) { this.normalizedSQL = normalizedSQL; }

    public List<String> getTokens() { return tokens; }
    public void setTokens(List<String> tokens) { this.tokens = tokens; }

    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }

    public List<String> getMatchedKeywordsForHighlight() { return matchedKeywordsForHighlight; }
    public void setMatchedKeywordsForHighlight(List<String> matchedKeywordsForHighlight) { this.matchedKeywordsForHighlight = matchedKeywordsForHighlight; }

    public List<String> getMatchedActions() { return matchedActions; }
    public void setMatchedActions(List<String> matchedActions) { this.matchedActions = matchedActions; }

    public boolean isDangerous() { return dangerous; }
    public void setDangerous(boolean dangerous) { this.dangerous = dangerous; }

    public boolean isDdl() { return ddl; }
    public void setDdl(boolean ddl) { this.ddl = ddl; }

    public boolean isMultiStatement() { return multiStatement; }
    public void setMultiStatement(boolean multiStatement) { this.multiStatement = multiStatement; }

    public boolean isContainsPLSQL() { return containsPLSQL; }
    public void setContainsPLSQL(boolean containsPLSQL) { this.containsPLSQL = containsPLSQL; }

    public boolean isPlsqlCreationDDL() { return plsqlCreationDDL; }
    public void setPlsqlCreationDDL(boolean plsqlCreationDDL) { this.plsqlCreationDDL = plsqlCreationDDL; }

    public String getStatementType() { return statementType; }
    public void setStatementType(String statementType) { this.statementType = statementType; }

    public String getPreviewSql() { return previewSql; }
    public void setPreviewSql(String previewSql) { this.previewSql = previewSql; }
    public boolean isParseSucceeded() { return parseSucceeded; }
    public void setParseSucceeded(boolean parseSucceeded) { this.parseSucceeded = parseSucceeded; }
}
