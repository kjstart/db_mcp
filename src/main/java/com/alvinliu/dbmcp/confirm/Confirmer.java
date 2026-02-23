package com.alvinliu.dbmcp.confirm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

/**
 * Human-in-the-loop confirmation: Windows = PowerShell WinForms (HTML SQL), Darwin = osascript dialog.
 * Same behavior as Go version.
 */
public class Confirmer {

    private static final String PS1_SCRIPT =
        "param([string]$HtmlPath, [string]$ResultPath, [string]$HeaderPath, [string]$Connection = \"default\")\n"
        + "$Header = if (Test-Path $HeaderPath) { [System.IO.File]::ReadAllText($HeaderPath, [System.Text.Encoding]::UTF8) } else { \"Confirm SQL execution\" }\n"
        + "Add-Type -AssemblyName System.Windows.Forms\n"
        + "Add-Type -AssemblyName System.Drawing\n"
        + "$fileUri = [Uri]::new(\"file:///\" + $HtmlPath.Replace('\\\\', '/').Replace(' ', '%20'))\n"
        + "$form = New-Object System.Windows.Forms.Form\n"
        + "$form.Text = \"Confirm SQL — \" + $Connection\n"
        + "$form.Size = New-Object System.Drawing.Size(1000, 780)\n"
        + "$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen\n"
        + "$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::Sizable\n"
        + "$form.MinimumSize = New-Object System.Drawing.Size(800, 600)\n"
        + "$form.TopMost = $true\n"
        + "$lbl = New-Object System.Windows.Forms.Label\n"
        + "$lbl.Text = $Header.Trim()\n"
        + "$lbl.Location = New-Object System.Drawing.Point(10, 10)\n"
        + "$lbl.AutoSize = $true\n"
        + "$lbl.MaximumSize = New-Object System.Drawing.Size(960, 0)\n"
        + "if ($Connection -and $Connection -ne \"default\") { $lbl.Font = New-Object System.Drawing.Font($lbl.Font.FontFamily, $lbl.Font.Size, [System.Drawing.FontStyle]::Bold) }\n"
        + "$form.Controls.Add($lbl)\n"
        + "$browser = New-Object System.Windows.Forms.WebBrowser\n"
        + "$browser.Location = New-Object System.Drawing.Point(10, 40)\n"
        + "$browser.Size = New-Object System.Drawing.Size(965, 620)\n"
        + "$browser.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right\n"
        + "$browser.ScrollBarsEnabled = $true\n"
        + "$browser.IsWebBrowserContextMenuEnabled = $false\n"
        + "$browser.ScriptErrorsSuppressed = $true\n"
        + "$browser.Navigate($fileUri.AbsoluteUri)\n"
        + "$btnExecute = New-Object System.Windows.Forms.Button\n"
        + "$btnExecute.Text = \"Execute\"\n"
        + "$btnExecute.Location = New-Object System.Drawing.Point(700, 670)\n"
        + "$btnExecute.Size = New-Object System.Drawing.Size(90, 28)\n"
        + "$btnExecute.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Right\n"
        + "$btnExecute.DialogResult = [System.Windows.Forms.DialogResult]::OK\n"
        + "$form.Controls.Add($btnExecute)\n"
        + "$btnCancel = New-Object System.Windows.Forms.Button\n"
        + "$btnCancel.Text = \"Cancel\"\n"
        + "$btnCancel.Location = New-Object System.Drawing.Point(800, 670)\n"
        + "$btnCancel.Size = New-Object System.Drawing.Size(90, 28)\n"
        + "$btnCancel.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Right\n"
        + "$btnCancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel\n"
        + "$form.Controls.Add($btnCancel)\n"
        + "$form.Controls.Add($browser)\n"
        + "$form.Controls.SetChildIndex($browser, 1)\n"
        + "$form.Add_Shown({ $form.ActiveControl = $browser })\n"
        + "$result = $form.ShowDialog()\n"
        + "$utf8NoBom = New-Object System.Text.UTF8Encoding $false\n"
        + "if ($result -eq [System.Windows.Forms.DialogResult]::OK) { [IO.File]::WriteAllText($ResultPath, \"1\", $utf8NoBom) }\n"
        + "else { [IO.File]::WriteAllText($ResultPath, \"0\", $utf8NoBom) }\n";

    /**
     * Show confirmation dialog. Returns true if user approved, false if cancelled or error.
     */
    public boolean confirm(ConfirmRequest req) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return confirmWindows(req);
        }
        if (os.contains("mac")) {
            return confirmDarwin(req);
        }
        System.err.println("[db_mcp] confirm: unsupported OS for dialog: " + os + "; treating as reject");
        return false;
    }

    private boolean confirmWindows(ConfirmRequest req) {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path htmlPath = tempDir.resolve("oracle-mcp-confirm-sql.html");
        Path resultPath = tempDir.resolve("oracle-mcp-confirm-result.txt");
        Path scriptPath = tempDir.resolve("oracle-mcp-confirm-dialog.ps1");
        Path headerPath = tempDir.resolve("oracle-mcp-confirm-header.txt");
        try {
            String html = req.getFormattedHtml();
            if (html == null || html.isBlank() || !html.trim().startsWith("<")) {
                html = sqlToHtml(req.getSql());
            }
            List<String> keywordsForHighlight = req.getMatchedKeywordsForHighlight() != null ? req.getMatchedKeywordsForHighlight() : req.getMatchedKeywords();
            html = highlightMatchedKeywords(html, keywordsForHighlight, req.getMatchedActions());
            Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
            Files.writeString(headerPath, buildHeader(req), StandardCharsets.UTF_8);
            Files.writeString(scriptPath, PS1_SCRIPT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[db_mcp] confirm: " + e.getMessage());
            return false;
        }
        String conn = req.getConnection() != null && !req.getConnection().isEmpty() ? req.getConnection() : "default";
        ProcessBuilder pb = new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toString(),
            "-HtmlPath", htmlPath.toString(), "-ResultPath", resultPath.toString(),
            "-HeaderPath", headerPath.toString(), "-Connection", conn);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process p = pb.start();
            p.waitFor(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[db_mcp] confirm: " + e.getMessage());
            return false;
        } finally {
            try { Files.deleteIfExists(htmlPath); } catch (IOException ignored) {}
            try { Files.deleteIfExists(headerPath); } catch (IOException ignored) {}
            try { Files.deleteIfExists(scriptPath); } catch (IOException ignored) {}
        }
        for (int i = 0; i < 20; i++) {
            try {
                if (Files.exists(resultPath)) {
                    byte[] data = Files.readAllBytes(resultPath);
                    data = trimBom(data);
                    String s = new String(data, StandardCharsets.UTF_8).trim();
                    try { Files.deleteIfExists(resultPath); } catch (IOException ignored) {}
                    return "1".equals(s);
                }
            } catch (IOException ignored) {}
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private static byte[] trimBom(byte[] b) {
        if (b.length >= 3 && b[0] == (byte) 0xEF && b[1] == (byte) 0xBB && b[2] == (byte) 0xBF) {
            byte[] out = new byte[b.length - 3];
            System.arraycopy(b, 3, out, 0, out.length);
            return out;
        }
        return b;
    }

    private boolean confirmDarwin(ConfirmRequest req) {
        String title = "Confirm SQL — " + (req.getConnection() != null ? req.getConnection() : "");
        if (title.endsWith(" — ")) title = "Dangerous SQL Detected";
        String message = buildMessage(req);
        message = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        String script = "display dialog \"" + message + "\" with title \"" + title.replace("\"", "\\\"") + "\" buttons {\"Cancel\", \"Execute\"} default button \"Cancel\" with icon caution";
        ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code == 1) return false;
            return out != null && out.contains("Execute");
        } catch (Exception e) {
            System.err.println("[db_mcp] confirm: " + e.getMessage());
            return false;
        }
    }

    private static String buildHeader(ConfirmRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getConnection() != null && !req.getConnection().isEmpty()) sb.append("Database: ").append(req.getConnection());
        // Action = command_match (statement types that triggered review)
        if (req.getMatchedActions() != null && !req.getMatchedActions().isEmpty()) {
            if (sb.length() > 0) sb.append("    |    ");
            sb.append("Action: ").append(String.join(", ", req.getMatchedActions()));
        } else if (req.getStatementType() != null && !req.getStatementType().isEmpty()) {
            if (sb.length() > 0) sb.append("    |    ");
            sb.append("Action: ").append(req.getStatementType());
        }
        // Keywords = whole_text_match
        if (req.getMatchedKeywords() != null && !req.getMatchedKeywords().isEmpty()) {
            if (sb.length() > 0) sb.append("    |    ");
            sb.append("Keywords: ").append(String.join(", ", req.getMatchedKeywords()));
        }
        if (req.isDdl()) {
            if (sb.length() > 0) sb.append("    |    ");
            sb.append("DDL (auto-committed)");
        }
        if (req.getSourceLabel() != null && !req.getSourceLabel().isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(req.getSourceLabel());
        }
        return sb.length() > 0 ? sb.toString() : "Confirm SQL execution";
    }

    private static String buildMessage(ConfirmRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getConnection() != null && !req.getConnection().isEmpty()) {
            sb.append("Database: ").append(req.getConnection()).append("\n\n");
        }
        if (req.getMatchedKeywords() != null && !req.getMatchedKeywords().isEmpty()) {
            sb.append("Keywords (whole_text_match): ").append(String.join(", ", req.getMatchedKeywords())).append("\n\n");
        }
        if (req.getMatchedActions() != null && !req.getMatchedActions().isEmpty()) {
            sb.append("Action (command_match): ").append(String.join(", ", req.getMatchedActions())).append("\n\n");
        } else if (req.getStatementType() != null) {
            sb.append("Statement Type: ").append(req.getStatementType()).append("\n\n");
        }
        sb.append("SQL:\n").append(req.getSql() != null ? req.getSql() : "").append("\n\n");
        if (req.isDdl()) sb.append("WARNING: DDL is auto-committed and cannot be rolled back!\n\n");
        if (req.getSourceLabel() != null && !req.getSourceLabel().isEmpty()) sb.append(req.getSourceLabel()).append("\n\n");
        return sb.toString();
    }

    /**
     * Match Go: show raw SQL with original line breaks. Normalize line endings to \\n, then to HTML (\\n-><br>, space->&nbsp;);
     * container uses white-space: pre-wrap to preserve layout.
     */
    private static String sqlToHtml(String sql) {
        if (sql == null) sql = "";
        String normalized = sql.replace("\r\n", "\n").replace("\r", "\n");
        String escaped = normalized
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
            .replace(" ", "&nbsp;");
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
            + ".sql-wrap { font-family: Consolas, monospace; font-size: 11pt; background: #fff; color: #24292e; padding: 12px; white-space: pre-wrap; word-break: break-word; overflow: visible; margin: 0; }"
            + "</style></head><body class=\"sql-wrap\"><code>" + escaped + "</code></body></html>";
    }

    private static final String HIGHLIGHT_SPAN = "<span style=\"color:red;font-weight:bold\">";
    private static final String HIGHLIGHT_SPAN_END = "</span>";

    /**
     * In HTML body, wrap matched keywords (whole_text + command_match) in red bold.
     * Only replaces in text content (not inside tags) and uses word boundary.
     */
    private static String highlightMatchedKeywords(String html, List<String> matchedKeywords, List<String> matchedActions) {
        if (html == null) return "";
        Set<String> set = new LinkedHashSet<>();
        if (matchedKeywords != null) set.addAll(matchedKeywords);
        if (matchedActions != null) set.addAll(matchedActions);
        List<String> list = new ArrayList<>(set);
        list.removeIf(kw -> kw == null || kw.isBlank());
        if (list.isEmpty()) return html;
        list.sort((a, b) -> Integer.compare(b.length(), a.length()));
        Pattern tagOrText = Pattern.compile("(<[^>]+>)|([^<]+)");
        Matcher m = tagOrText.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            if (m.group(1) != null) {
                out.append(m.group(1));
            } else {
                String text = m.group(2);
                for (String kw : list) {
                    String quoted = Pattern.quote(kw);
                    text = text.replaceAll("(?i)\\b" + quoted + "\\b", HIGHLIGHT_SPAN + "$0" + HIGHLIGHT_SPAN_END);
                }
                out.append(text);
            }
        }
        return out.toString();
    }
}
