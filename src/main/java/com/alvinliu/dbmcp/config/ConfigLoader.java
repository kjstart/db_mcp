package com.alvinliu.dbmcp.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Load config from config.yaml (current dir or DB_MCP_CONFIG env).
 */
public final class ConfigLoader {
    private static final String CONFIG_ENV = "DB_MCP_CONFIG";

    public static Config load() throws IOException {
        Path path = findConfigPath();
        if (path == null) {
            throw new IOException("config file not found: create config.yaml or set " + CONFIG_ENV);
        }
        return loadFromFile(path);
    }

    @SuppressWarnings("unchecked")
    public static Config loadFromFile(Path path) throws IOException {
        Yaml yaml = new Yaml();
        String content = Files.readString(path);
        Map<String, Object> raw = yaml.load(content);
        Config cfg = new Config();
        cfg.setConfigPath(path.toAbsolutePath().toString());
        if (raw == null) return cfg;
        Object conns = raw.get("connections");
        if (conns instanceof List) {
            List<ConnectionEntry> entries = new ArrayList<>();
            boolean anyPlain = false;
            for (Object o : (List<?>) conns) {
                if (o instanceof Map) {
                    ConnectionEntry entry = entryFromMap((Map<String, Object>) o);
                    if (hasPlainCredentials(entry)) anyPlain = true;
                    entries.add(entry);
                }
            }
            cfg.setConnections(entries);
            if (anyPlain) {
                encryptAndSave(path, content, entries);
            }
        }
        Object rev = raw.get("review");
        if (rev instanceof Map) {
            cfg.setReview(reviewFromMap((Map<String, Object>) rev));
        }
        Object log = raw.get("logging");
        if (log instanceof Map) {
            cfg.setLogging(loggingFromMap((Map<String, Object>) log));
        }
        return cfg;
    }

    private static boolean hasPlainCredentials(ConnectionEntry e) {
        return (e.getPassword() != null && !e.getPassword().isEmpty() && !ConnectionCrypto.isEncrypted(e.getPassword()))
            || (e.getUser() != null && !e.getUser().isEmpty() && !ConnectionCrypto.isEncrypted(e.getUser()))
            || (e.getUrl() != null && !e.getUrl().isEmpty() && !ConnectionCrypto.isEncrypted(e.getUrl()));
    }

    /**
     * Encrypt plain-text url, user, and password fields in-place in config.yaml,
     * preserving all comments and formatting.
     */
    private static void encryptAndSave(Path path, String originalContent, List<ConnectionEntry> entries) {
        try {
            String[] lines = originalContent.split("\n", -1);
            for (ConnectionEntry entry : entries) {
                if (entry.getUrl() != null && !entry.getUrl().isEmpty()
                        && !ConnectionCrypto.isEncrypted(entry.getUrl())) {
                    String encrypted = ConnectionCrypto.encrypt(entry.getUrl());
                    lines = replaceFieldValue(lines, "url", entry.getUrl(), encrypted);
                }
                if (entry.getUser() != null && !entry.getUser().isEmpty()
                        && !ConnectionCrypto.isEncrypted(entry.getUser())) {
                    String encrypted = ConnectionCrypto.encrypt(entry.getUser());
                    lines = replaceFieldValue(lines, "user", entry.getUser(), encrypted);
                }
                if (entry.getPassword() != null && !entry.getPassword().isEmpty()
                        && !ConnectionCrypto.isEncrypted(entry.getPassword())) {
                    String encrypted = ConnectionCrypto.encrypt(entry.getPassword());
                    lines = replaceFieldValue(lines, "password", entry.getPassword(), encrypted);
                }
            }
            Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[db_mcp] Warning: failed to encrypt config credentials: " + e.getMessage());
        }
    }

    /**
     * Replace the first occurrence of "  fieldName: plainValue" with "  fieldName: encryptedValue"
     * in the lines array. Preserves indentation and surrounding lines.
     */
    private static String[] replaceFieldValue(String[] lines, String field, String plain, String encrypted) {
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith(field + ":")) {
                String rest = trimmed.substring(field.length() + 1).trim();
                String unquoted = rest.replaceAll("^['\"]|['\"]$", "");
                if (unquoted.equals(plain)) {
                    String indent = lines[i].substring(0, lines[i].length() - lines[i].stripLeading().length());
                    lines[i] = indent + field + ": " + encrypted;
                    break;
                }
            }
        }
        return lines;
    }

    private static LoggingConfig loggingFromMap(Map<String, Object> m) {
        LoggingConfig l = new LoggingConfig();
        Object v = m.get("audit_log");
        if (v instanceof Boolean) l.setAuditLog((Boolean) v);
        v = m.get("mcp_console_log");
        if (v instanceof Boolean) l.setMcpConsoleLog((Boolean) v);
        String f = getStr(m, "log_file");
        if (f != null) l.setLogFile(f);
        return l;
    }

    private static ConnectionEntry entryFromMap(Map<String, Object> m) {
        ConnectionEntry e = new ConnectionEntry();
        e.setName(getStr(m, "name"));
        e.setDriver(getStr(m, "driver"));
        e.setDbType(getStr(m, "db_type"));
        e.setUrl(decryptIfNeeded(getStr(m, "url")));
        e.setUser(decryptIfNeeded(getStr(m, "user")));
        e.setPassword(decryptIfNeeded(getStr(m, "password")));
        e.setSchema(getStr(m, "schema"));
        e.setDatabase(getStr(m, "database"));
        return e;
    }

    private static String decryptIfNeeded(String value) {
        if (value == null) return null;
        if (!ConnectionCrypto.isEncrypted(value)) return value;
        try {
            return ConnectionCrypto.decrypt(value);
        } catch (Exception ex) {
            System.err.println("[db_mcp] Warning: failed to decrypt config value: " + ex.getMessage());
            return value;
        }
    }

    private static SecurityConfig reviewFromMap(Map<String, Object> m) {
        SecurityConfig s = new SecurityConfig();
        Object kwWhole = m.get("whole_text_match");
        if (kwWhole instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) kwWhole) {
                if (o != null) list.add(o.toString().trim());
            }
            s.setWholeTextMatch(list);
        }
        Object kwCmd = m.get("command_match");
        if (kwCmd instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) kwCmd) {
                if (o != null) list.add(o.toString().trim());
            }
            s.setCommandMatch(list);
        }
        Object req = m.get("always_review_ddl");
        if (req instanceof Boolean) s.setAlwaysReviewDdl((Boolean) req);
        return s;
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }

    static Path findConfigPath() {
        String env = System.getenv(CONFIG_ENV);
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env);
            if (Files.isRegularFile(p)) return p;
        }
        Path cwd = Paths.get("").toAbsolutePath();
        Path yaml = cwd.resolve("config.yaml");
        if (Files.isRegularFile(yaml)) return yaml;
        return null;
    }
}
