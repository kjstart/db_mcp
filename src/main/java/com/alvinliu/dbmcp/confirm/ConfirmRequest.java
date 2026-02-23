package com.alvinliu.dbmcp.confirm;

import java.util.List;

/**
 * Data for the confirmation dialog. Aligned with Go ConfirmRequest.
 */
public class ConfirmRequest {
    private String sql;
    private List<String> matchedKeywords;   // whole_text_match: for "Keywords" on dialog (union of original + formatted hits)
    private List<String> matchedKeywordsForHighlight; // whole_text_match: only hits on formatted text, for highlighting in HTML
    private List<String> matchedActions;     // command_match: for "Action" on dialog
    private String statementType;            // backward compat / first action
    private boolean ddl;
    private String connection;
    private String sourceLabel;
    private String databaseName;
    private String schema;
    private String driver;
    private String formattedHtml;

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }

    public List<String> getMatchedKeywordsForHighlight() { return matchedKeywordsForHighlight; }
    public void setMatchedKeywordsForHighlight(List<String> matchedKeywordsForHighlight) { this.matchedKeywordsForHighlight = matchedKeywordsForHighlight; }

    public List<String> getMatchedActions() { return matchedActions; }
    public void setMatchedActions(List<String> matchedActions) { this.matchedActions = matchedActions; }

    public String getStatementType() { return statementType; }
    public void setStatementType(String statementType) { this.statementType = statementType; }

    public boolean isDdl() { return ddl; }
    public void setDdl(boolean ddl) { this.ddl = ddl; }

    public String getConnection() { return connection; }
    public void setConnection(String connection) { this.connection = connection; }

    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getFormattedHtml() { return formattedHtml; }
    public void setFormattedHtml(String formattedHtml) { this.formattedHtml = formattedHtml; }
}
