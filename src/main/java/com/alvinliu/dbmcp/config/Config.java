package com.alvinliu.dbmcp.config;

import java.util.ArrayList;
import java.util.List;

public class Config {
    private List<ConnectionEntry> connections = new ArrayList<>();
    private SecurityConfig review = new SecurityConfig();
    private LoggingConfig logging = new LoggingConfig();
    private String configPath;

    public List<ConnectionEntry> getConnections() { return connections; }
    public void setConnections(List<ConnectionEntry> connections) { this.connections = connections != null ? connections : new ArrayList<>(); }

    public SecurityConfig getReview() { return review; }
    public void setReview(SecurityConfig review) { this.review = review != null ? review : new SecurityConfig(); }

    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging != null ? logging : new LoggingConfig(); }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }
}
