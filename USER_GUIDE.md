<a id="english"></a>

[English](#english) | [中文](#chinese)

# db_mcp — Database MCP Server — User Guide

**Author:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**Project:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

This guide covers the **db_mcp** MCP server for **any MCP-compatible AI client** (Cursor, Claude Code, OpenClaw, etc.). It supports **any JDBC database** (Oracle, MySQL, PostgreSQL, SQL Server, and more). You need **Java 11+**, **Maven 3.x** (to build), the **fat JAR** from `mvn package`, and **JDBC driver JAR(s)** for your database. Configuration is in `config.yaml` (copy from `config.yaml.example`).

---

## 1. Prerequisites: Java and JDBC driver

1. **Install Java 11 or later**
   - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/).
   - Ensure `java -version` works in your terminal.

2. **Get the JDBC driver JAR for your database**
   - If you can find a JDBC driver for your database, you can use this MCP to connect. Examples for common databases:
   - **Oracle:** [Oracle JDBC Driver](https://www.oracle.com/database/technologies/app-dev/jdbc-downloads.html) (e.g. `ojdbc11.jar`).
   - **MySQL:** [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/) or add via Maven.
   - **PostgreSQL:** [PostgreSQL JDBC](https://jdbc.postgresql.org/download/) or Maven.
   - **SQL Server:** [Microsoft JDBC Driver](https://docs.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server).

3. **Place driver JAR(s)**
   - Put the driver JAR(s) in `db_mcp/lib/` (or the directory you use in the classpath when configuring your MCP client in section 4). The fat JAR does **not** include drivers.

---

## 2. Configure config.yaml

1. **Copy the example config**
   - In the `db_mcp` folder, copy `config.yaml.example` and rename the copy to `config.yaml`.

2. **Edit config.yaml**
   - Open `config.yaml` in a text editor.
   - Configure **connections** (required, at least one entry). Each entry has a **name** that you use as the `connection` argument in tools; call `list_connections` to see names.

   ```yaml
   connections:
     - name: database1
       driver: oracle.jdbc.OracleDriver
       url: "jdbc:oracle:thin:@//host:1521/ORCL"
       user: myuser
       password: mypass
       db_type: oracle   # optional; default mysql. For SQL parsing/formatting.
     # - name: database2
     #   driver: com.mysql.cj.jdbc.Driver
     #   url: "jdbc:mysql://localhost:3306/mydb"
     #   user: root
     #   password: secret
     #   db_type: mysql
   ```

   - **driver** — Full JDBC driver class name (the driver JAR must be on the classpath).
   - **url** — JDBC URL (e.g. `jdbc:oracle:thin:@//host:1521/ORCL`, `jdbc:mysql://localhost:3306/mydb`).
   - **user** / **password** — Optional if encoded in the URL.
   - **db_type** (optional) — Database type for SQL parsing/formatting (Druid dialect). Default is `mysql` if omitted. See **db_type reference** below.

   > **Credential encryption:** On first startup, the server automatically encrypts the plain-text `url`, `user`, and `password` values in `config.yaml`. Subsequent startups decrypt them transparently — no manual steps needed.

   **db_type reference (Druid DbType)** — In config use the **db_type** value in lower case. Full source: [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java). Examples by category:

   | Category | Examples (full name → db_type) |
   |----------|--------------------------------|
   | **Common RDBMS** | Oracle (`oracle`), MySQL (`mysql`), MariaDB (`mariadb`), PostgreSQL (`postgresql`), SQL Server (`sqlserver`), DB2 (`db2`), H2 (`h2`), SQLite (`sqlite`), Sybase (`sybase`) |
   | **China Database** | DaMeng 达梦 (`dm`), Kingbase 人大金仓 (`kingbase`), Gbase (`gbase`), Oscar 神州通用 (`oscar`) |
   | **Cloud / Managed** | OceanBase (`oceanbase`, `oceanbase_oracle`), PolarDB (`polardb`, `polardb2`, `polardbx`), Snowflake (`snowflake`), BigQuery (`bigquery`), Redshift (`redshift`), Athena (`athena`), Databricks (`databricks`), Azure Synapse (`synapse`) |
   | **MPP / Analytics** | Greenplum (`greenplum`), GaussDB (`gaussdb`), ClickHouse (`clickhouse`), Doris (`doris`), StarRocks (`starrocks`), Presto (`presto`), Trino (`trino`) |
   | **Big Data** | Hive (`hive`), HBase (`hbase`), TiDB (`tidb`), Spark (`spark`), Teradata (`teradata`) |

   More types (e.g. `elastic_search`, `odps`, `mock`, `other`) — see [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java).

   - **One connection:** all SQL runs against that database; you don't need to pass `connection`.
   - **Multiple connections:** pass `"connection": "database1"` (or the name you configured) when calling tools.

   **Review and logging** are optional. Omit the `review` and `logging` sections to disable. See `config.yaml.example` for `whole_text_match`, `command_match`, `always_review_ddl`, `audit_log`, `mcp_console_log`, and `log_file`.

3. **Config file location**
   - Keep `config.yaml` in a known location and set the environment variable **`DB_MCP_CONFIG`** to its **absolute path** when you configure the MCP server in your client (section 4). The server reads config from `DB_MCP_CONFIG` first.

---

## 3. Build and run (optional: run from terminal)

- **Java 11+**, **Maven 3.x**

```bash
cd db_mcp
mvn compile
mvn exec:java -Dexec.mainClass="com.alvinliu.dbmcp.DBMCPServer"
```

To build a **fat JAR** (needed for MCP clients):

```bash
cd db_mcp
mvn package
```

The fat JAR is in `target/` (filename pattern: `db-mcp-*-fat.jar`; the middle part is your build version). Put your driver JAR(s) in `lib/`. Use **`-cp` + main class** (not `-jar`) so both the fat JAR and `lib/*` are on the classpath.

---

## 4. Configure the MCP server in your client

Attach db_mcp to your AI client by running the server over **stdio**. Paths below use placeholders: replace them with your actual install path and config file. Use the fat JAR version you built (e.g. `1.0.0-SNAPSHOT` from source).

### 4.1 Cursor

1. **Open MCP settings**
   - In Cursor: **File** → **Preferences** → **Cursor Settings** → **MCP**
   - Or edit the config file directly:
     - **Windows:** `C:\Users\<YourUsername>\.cursor\mcp.json`
     - **macOS / Linux:** `~/.cursor/mcp.json`
   - Project-only: create `.cursor/mcp.json` in the project root.

2. **Add the db_mcp server**

   **Windows** — classpath separator `;`:
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```
   Replace `D:/path/to/db_mcp` with your actual path (use forward slashes or escaped backslashes in JSON). Replace `<version>` with your JAR version (e.g. `1.0.0-SNAPSHOT`).

   **Linux / macOS** — classpath separator `:`:
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```

3. **Restart Cursor**
   - Save `mcp.json`, then **fully quit and reopen Cursor** so the MCP server is loaded.

4. **Verify**
   - If database MCP tools (`list_connections`, `execute_sql`, `execute_sql_file`, `query_to_csv_file`, `query_to_text_file`) appear in your chat, the setup is working.
   - With multiple databases: call `list_connections` to see names, then use `execute_sql` (or other tools) with `"connection": "database1"` to run on a specific database.

### 4.2 Claude Code

Use the **Claude Code** CLI to add db_mcp as an MCP server. Run one of the following in a terminal, then replace the path placeholders with your actual paths and `<version>` with your JAR version (e.g. `1.0.0-SNAPSHOT`).

**Windows** — classpath separator `;`:
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="D:/path/to/db_mcp/config.yaml" -- java -cp "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

**Linux / macOS** — classpath separator `:`:
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="/path/to/db_mcp/config.yaml" -- java -cp "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

Example (Windows, real paths):
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="D:/work/code/db_mcp/config.yaml" -- java -cp "D:/work/code/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar;D:/work/code/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

After adding, Claude Code can use the db_mcp tools. To remove: `claude mcp remove db-mcp`.

### 4.3 OpenClaw

In **OpenClaw**, you add an MCP server by **sending a conversation message**. Paste one of the following messages (replace path placeholders with your actual paths and `<version>` with your JAR version, e.g. `1.0.0-SNAPSHOT`).

**Windows** — use backslashes in paths; classpath separator `;`:
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp "D:\path\to\db_mcp\target\db-mcp-<version>-fat.jar;D:\path\to\db_mcp\lib\*" com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to 'D:\path\to\db_mcp\config.yaml'.
```

**Linux / macOS** — classpath separator `:`:
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp /path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/* com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to '/path/to/db_mcp/config.yaml'.
```

Example (Windows, real paths):
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp "D:\work\code\db_mcp\target\db-mcp-1.0.0-SNAPSHOT-fat.jar;D:\work\code\db_mcp\lib\*" com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to 'D:\work\code\db_mcp\config.yaml'.
```

---

### 4.4 Claude Code for VS Code (VS Code 版本)

> **注意**：Claude Code 和 Claude Code for VS Code（VS Code 版本）**不共享** MCP 配置。如果你已经在普通 Claude Code 中配置过 db_mcp，VS Code 版本需要单独配置。尤其是当 VS Code 版本配置了自定义 LLM API 时，两者更要注意分开配置。

1. **在项目中创建 `.mcp.json` 文件**
   - 在你的项目根目录（如 `.cursor/` 或其他项目文件夹）下创建 `.mcp.json` 文件。
   - 配置示例：

   **Windows** — classpath 分隔符 `;`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```
   将 `D:/path/to/db_mcp` 替换为你的实际路径（JSON 中可用正斜杠或转义反斜杠）。将 `<version>` 替换为你的 JAR 版本（如 `1.0.0-SNAPSHOT`）。

   **Linux / macOS** — classpath 分隔符 `:`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```

2. **重启 VS Code**
   - 保存 `.mcp.json` 后**完全退出并重新打开 VS Code**，以加载 MCP 服务。

3. **验证**
   - 打开 **Claude Code** 插件。
   - 在对话中输入 `/mcp`。
   - 如果能看到已加载的 MCP 服务器列表（包含 `db-mcp`），说明配置成功。
   - 若对话中出现数据库相关 MCP 工具（`list_connections`、`execute_sql`、`execute_sql_file`、`query_to_csv_file`、`query_to_text_file`），说明 db_mcp 已正常工作。
   - 多数据库时：先调用 `list_connections` 查看名称，再在 `execute_sql` 等工具中传入 `"connection": "database1"` 对指定库执行。

---

## 5. Tools and behaviour

- **list_connections** — List configured connection names, availability, and `db_type`. Each call re-checks connections; previously failed ones are retried. Use the returned names as the `connection` argument in other tools.
- **execute_sql** — Run SQL on the chosen connection (multi-statement, semicolon-separated). Supports JDBC escape `{ call proc() }` / `{ ? = call func(?) }` for stored procedures/functions; on Oracle only, anonymous blocks (`BEGIN...END`, `DECLARE...BEGIN...END`) are supported. Params: `sql`, optional `connection`. Some SQL may require user approval; if rejected, the client receives an execution-cancelled result. Schema-qualified object names (e.g. `hr.employees`) are not allowed and will be rejected.
- **execute_sql_file** — Read SQL from a file, apply the same rules as `execute_sql`, then execute. **Callers must use an absolute path** for `file_path`. Trailing SQL*Plus `/`-only lines are stripped. Params: `file_path`, optional `connection`.
- **query_to_csv_file** — Run a query and write the result to a file as CSV (header + rows, UTF-8). Params: `sql`, `file_path` (absolute), optional `connection`.
- **query_to_text_file** — Run a query and write the result to a file as plain text (tab-separated columns per line). Params: `sql`, `file_path` (absolute), optional `connection`.

**Audit log** (if enabled in config): each entry includes connection and database info so you can see which database was used.

**Connection failures:** On connection/IO errors, the server marks that connection as unavailable. Subsequent calls to that connection fail fast until you fix the database and call **list_connections** again; only **list_connections** re-validates and can clear the unavailable state.

---

## Troubleshooting

| Symptom | Likely cause | What to do |
|--------|----------------|------------|
| "java" not found or wrong version | Java not installed or not on PATH | Install Java 11+ and ensure `java` is on the PATH used by your client when it starts the MCP process. |
| ClassNotFoundException (driver) | JDBC driver JAR not on classpath | Put the driver JAR (e.g. `ojdbc11.jar`) in `db_mcp/lib/` and use `-cp` with `.../db_mcp/target/*-fat.jar` and `.../db_mcp/lib/*` (Windows: `;`, Linux/macOS: `:`). Do not use `-jar` alone. |
| Error about missing config | Config file not found | Set `DB_MCP_CONFIG` in the MCP `env` to the **absolute path** of `config.yaml`. |
| Connection unavailable / fast-fail | Database down or unreachable | Check the database and network. Then call **list_connections** again to re-validate; only that tool clears the unavailable state. |
| Database tools not visible in client | MCP not loaded or wrong path | Check the `command` and `args` in your client's MCP config (paths, classpath separator), ensure the fat JAR and `lib/*` are correct, and restart the client. |

---

<a id="chinese"></a>

[English](#english) | [中文](#chinese)

# db_mcp — Database MCP Server — 用户指南（中文）

**作者:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**项目地址:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

本指南面向 **db_mcp** MCP 服务端，适用于 **任意支持 MCP 的 AI 客户端**（Cursor、Claude Code 等）。支持 **任意 JDBC 数据库**（Oracle、MySQL、PostgreSQL、SQL Server 等）。需要 **Java 11+**、**Maven 3.x**（用于构建）、通过 `mvn package` 得到的 **fat JAR**，以及所用数据库的 **JDBC 驱动 JAR**。配置写在 `config.yaml` 中（可从 `config.yaml.example` 复制后修改）。

---

## 1. 前置条件：Java 与 JDBC 驱动

1. **安装 Java 11 或更高版本**
   - 从 [Oracle](https://www.oracle.com/java/technologies/downloads/) 下载并安装。
   - 在终端中执行 `java -version` 确认可用。

2. **准备所用数据库的 JDBC 驱动 JAR**
   - 只要能为你的数据库找到 JDBC 驱动，就可以用本 MCP 连接。常见数据库示例：
   - **Oracle：** [Oracle JDBC 驱动](https://www.oracle.com/database/technologies/app-dev/jdbc-downloads.html)（如 `ojdbc11.jar`）。
   - **MySQL：** [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/) 或通过 Maven 引入。
   - **PostgreSQL：** [PostgreSQL JDBC](https://jdbc.postgresql.org/download/) 或 Maven。
   - **SQL Server：** [Microsoft JDBC Driver](https://docs.microsoft.com/zh-cn/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server)。

3. **放置驱动 JAR**
   - 将驱动 JAR 放在 `db_mcp/lib/`（或你在第 4 步配置 MCP 客户端时 classpath 中使用的目录）。fat JAR **不包含** 驱动，需单独放置。

---

## 2. 配置 config.yaml

1. **复制示例配置**
   - 在 `db_mcp` 目录下，将 `config.yaml.example` 复制一份并重命名为 `config.yaml`。

2. **编辑 config.yaml**
   - 用任意文本编辑器打开 `config.yaml`。
   - 配置 **connections**（必填，至少一项）。每项的 **name** 将作为各工具中的 `connection` 参数使用；可调用 `list_connections` 查看名称。

   ```yaml
   connections:
     - name: database1
       driver: oracle.jdbc.OracleDriver
       url: "jdbc:oracle:thin:@//host:1521/ORCL"
       user: myuser
       password: mypass
       db_type: oracle   # 可选；默认 mysql。用于 SQL 解析与格式化。
     # - name: database2
     #   driver: com.mysql.cj.jdbc.Driver
     #   url: "jdbc:mysql://localhost:3306/mydb"
     #   user: root
     #   password: secret
     #   db_type: mysql
   ```

   - **driver** — JDBC 驱动完整类名（对应 JAR 需在 classpath 中）。
   - **url** — JDBC URL（如 `jdbc:oracle:thin:@//host:1521/ORCL`、`jdbc:mysql://localhost:3306/mydb`）。
   - **user** / **password** — 若已在 URL 中编码可省略。
   - **db_type**（可选）— 用于 SQL 解析与格式化的数据库类型（Druid 方言）。不填时默认为 `mysql`。见下方 **db_type 对照**。

   > **连接信息自动加密：** 首次启动时，服务端会自动将 `config.yaml` 中明文的 `url`、`user`、`password` 加密写回文件，后续启动透明解密，无需手动操作。

   **db_type 对照（Druid DbType）** — 配置中填写小写的 **db_type** 取值。完整枚举见 [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java)。按分类示例：

   | 分类 | 示例（全名 → db_type） |
   |------|------------------------|
   | **常用关系型** | Oracle (`oracle`)、MySQL (`mysql`)、MariaDB (`mariadb`)、PostgreSQL (`postgresql`)、SQL Server (`sqlserver`)、DB2 (`db2`)、H2 (`h2`)、SQLite (`sqlite`)、Sybase (`sybase`) |
   | **国产数据库** | 达梦 DaMeng (`dm`)、人大金仓 Kingbase (`kingbase`)、Gbase (`gbase`)、神州通用 Oscar (`oscar`) |
   | **云 / 托管** | OceanBase (`oceanbase`、`oceanbase_oracle`)、PolarDB (`polardb`、`polardb2`、`polardbx`)、Snowflake (`snowflake`)、BigQuery (`bigquery`)、Redshift (`redshift`)、Athena (`athena`)、Databricks (`databricks`)、Azure Synapse (`synapse`) |
   | **MPP / 分析** | Greenplum (`greenplum`)、GaussDB (`gaussdb`)、ClickHouse (`clickhouse`)、Doris (`doris`)、StarRocks (`starrocks`)、Presto (`presto`)、Trino (`trino`) |
   | **大数据** | Hive (`hive`)、HBase (`hbase`)、TiDB (`tidb`)、Spark (`spark`)、Teradata (`teradata`) |

   更多类型（如 `elastic_search`、`odps`、`mock`、`other`）见 [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java)。

   - **单连接：** 所有 SQL 都发往该数据库，无需传 `connection`。
   - **多连接：** 调用工具时传入 `"connection": "database1"`（或你配置的名称）。

   **审查与日志** 为可选。不配置 `review` 和 `logging` 即不启用。完整示例见 `config.yaml.example`（`whole_text_match`、`command_match`、`always_review_ddl`、`audit_log`、`mcp_console_log`、`log_file`）。

3. **配置文件位置**
   - 将 `config.yaml` 放在固定位置，并在客户端 MCP 配置（第 4 步）中设置环境变量 **`DB_MCP_CONFIG`** 为其 **绝对路径**。服务端优先读取该路径。

---

## 3. 编译与运行（可选：从终端运行）

- **Java 11+**、**Maven 3.x**

```bash
cd db_mcp
mvn compile
mvn exec:java -Dexec.mainClass="com.alvinliu.dbmcp.DBMCPServer"
```

若要打 **fat JAR**（供 MCP 客户端使用）：

```bash
cd db_mcp
mvn package
```

生成的 fat JAR 在 `target/`（文件名形如 `db-mcp-*-fat.jar`，中间为当前构建版本号）。将驱动 JAR 放在 `lib/`。启动时使用 **`-cp` + 主类**（不要单独用 `-jar`），以便把 fat JAR 与 `lib/*` 都加入 classpath。

---

## 4. 在客户端中配置 MCP 服务

通过 **stdio** 启动 db_mcp 并挂到你的 AI 客户端。以下路径为占位符，请替换为你的实际安装路径和配置文件；`<version>` 替换为你的 JAR 版本（如从源码构建时为 `1.0.0-SNAPSHOT`）。

### 4.1 Cursor

1. **打开 MCP 设置**
   - Cursor：**File** → **Preferences** → **Cursor Settings** → **MCP**
   - 或直接编辑配置文件：
     - **Windows：** `C:\Users\<你的用户名>\.cursor\mcp.json`
     - **macOS / Linux：** `~/.cursor\mcp.json`
   - 仅当前项目：在项目根目录创建 `.cursor/mcp.json`。

2. **添加 db_mcp 服务**

   **Windows** — classpath 分隔符 `;`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```
   将 `D:/path/to/db_mcp` 替换为你的实际路径（JSON 中可用正斜杠或转义反斜杠）。将 `<version>` 替换为你的 JAR 版本（如 `1.0.0-SNAPSHOT`）。

   **Linux / macOS** — classpath 分隔符 `:`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```

3. **重启 Cursor**
   - 保存 `mcp.json` 后**完全退出并重新打开 Cursor**，以加载 MCP 服务。

4. **验证**
   - 若对话中出现数据库相关 MCP 工具（`list_connections`、`execute_sql`、`execute_sql_file`、`query_to_csv_file`、`query_to_text_file`），说明配置成功。
   - 多数据库时：先调用 `list_connections` 查看名称，再在 `execute_sql` 等工具中传入 `"connection": "database1"` 对指定库执行。

### 4.2 Claude Code

使用 **Claude Code** 命令行将 db_mcp 添加为 MCP 服务。在终端中执行下面其中一条命令，并将路径占位符替换为你的实际路径，`<version>` 替换为你的 JAR 版本（如 `1.0.0-SNAPSHOT`）。

**Windows** — classpath 分隔符 `;`：
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="D:/path/to/db_mcp/config.yaml" -- java -cp "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

**Linux / macOS** — classpath 分隔符 `:`：
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="/path/to/db_mcp/config.yaml" -- java -cp "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

示例（Windows，真实路径）：
```bash
claude mcp add db-mcp -e DB_MCP_CONFIG="D:/work/code/db_mcp/config.yaml" -- java -cp "D:/work/code/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar;D:/work/code/db_mcp/lib/*" com.alvinliu.dbmcp.DBMCPServer
```

添加后，Claude Code 即可使用 db_mcp 提供的工具。移除服务：`claude mcp remove db-mcp`。

### 4.3 OpenClaw

在 **OpenClaw** 中，通过**发送一条对话消息**添加 MCP 服务。将下面其中一条消息粘贴发送（将路径占位符替换为本机实际路径，`<version>` 替换为你的 JAR 版本，如 `1.0.0-SNAPSHOT`）。

**Windows** — 路径使用反斜杠；classpath 分隔符 `;`：
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp "D:\path\to\db_mcp\target\db-mcp-<version>-fat.jar;D:\path\to\db_mcp\lib\*" com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to 'D:\path\to\db_mcp\config.yaml'.
```

**Linux / macOS** — classpath 分隔符 `:`：
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp /path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/* com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to '/path/to/db_mcp/config.yaml'.
```

示例（Windows，真实路径）：
```
Add a new MCP server called 'db-mcp' using stdio. The command is 'java', the args are '-cp "D:\work\code\db_mcp\target\db-mcp-1.0.0-SNAPSHOT-fat.jar;D:\work\code\db_mcp\lib\*" com.alvinliu.dbmcp.DBMCPServer', and set the environment variable DB_MCP_CONFIG to 'D:\work\code\db_mcp\config.yaml'.
```

---

### 4.4 Claude Code for VS Code（VS Code 版本）

> **注意**：Claude Code 和 Claude Code for VS Code（VS Code 版本）**不共享** MCP 配置。如果你已经在普通 Claude Code 中配置过 db_mcp，VS Code 版本需要单独配置。尤其是当 VS Code 版本配置了自定义 LLM API 时，两者更要注意分开配置。

1. **在项目中创建 `.mcp.json` 文件**
   - 在你的项目根目录（如 `.cursor/` 或其他项目文件夹）下创建 `.mcp.json` 文件。
   - 配置示例：

   **Windows** — classpath 分隔符 `;`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "D:/path/to/db_mcp/target/db-mcp-<version>-fat.jar;D:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```
   将 `D:/path/to/db_mcp` 替换为你的实际路径（JSON 中可用正斜杠或转义反斜杠）。将 `<version>` 替换为你的 JAR 版本（如 `1.0.0-SNAPSHOT`）。

   **Linux / macOS** — classpath 分隔符 `:`：
   ```json
   {
     "mcpServers": {
       "db-mcp": {
         "command": "java",
         "args": [
           "-cp",
           "/path/to/db_mcp/target/db-mcp-<version>-fat.jar:/path/to/db_mcp/lib/*",
           "com.alvinliu.dbmcp.DBMCPServer"
         ],
         "env": {
           "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
         }
       }
     }
   }
   ```

2. **重启 VS Code**
   - 保存 `.mcp.json` 后**完全退出并重新打开 VS Code**，以加载 MCP 服务。

3. **验证**
   - 打开 **Claude Code** 插件。
   - 在对话中输入 `/mcp`。
   - 如果能看到已加载的 MCP 服务器列表（包含 `db-mcp`），说明配置成功。
   - 若对话中出现数据库相关 MCP 工具（`list_connections`、`execute_sql`、`execute_sql_file`、`query_to_csv_file`、`query_to_text_file`），说明 db_mcp 已正常工作。
   - 多数据库时：先调用 `list_connections` 查看名称，再在 `execute_sql` 等工具中传入 `"connection": "database1"` 对指定库执行。

---

## 5. 工具与行为

- **list_connections** — 列出已配置连接名称、可用性及 `db_type`。每次调用会重新检查连接，对之前失败的连接会重试。将返回的名称作为其他工具的 `connection` 参数使用。
- **execute_sql** — 在指定连接上执行 SQL（支持多语句，分号分隔）。支持 JDBC 转义 `{ call proc() }` / `{ ? = call func(?) }` 调用存储过程/函数；仅 Oracle 支持匿名块（`BEGIN...END`、`DECLARE...BEGIN...END`）。参数：`sql`，可选 `connection`。部分 SQL 可能需用户确认；若用户拒绝，客户端会收到执行已取消的结果。不允许使用 schema 限定名（如 `hr.employees`），否则会被拒绝执行。
- **execute_sql_file** — 从文件读取 SQL，应用与 `execute_sql` 相同规则后执行。**调用方请对 `file_path` 使用绝对路径**。末尾仅含 `/` 的 SQL*Plus 行会被去除。参数：`file_path`，可选 `connection`。
- **query_to_csv_file** — 执行查询并将结果以 CSV（表头 + 行，UTF-8）写入文件。参数：`sql`、`file_path`（绝对路径）、可选 `connection`。
- **query_to_text_file** — 执行查询并将结果以纯文本（每行制表符分隔列）写入文件。参数：`sql`、`file_path`（绝对路径）、可选 `connection`。

**审计日志**（若在配置中启用）：每条记录包含连接与数据库信息，便于查看使用的数据库。

**连接失败：** 发生连接/IO 错误时，服务端会将该连接标记为不可用。之后对该连接的调用会快速失败，直到你修复数据库并再次调用 **list_connections**；只有 **list_connections** 会重新校验并可能清除不可用状态。

---

## 故障排除

| 现象 | 可能原因 | 处理 |
|--------|----------------|------------|
| 找不到 "java" 或版本不对 | 未安装 Java 或未加入 PATH | 安装 Java 11+，并确保客户端启动 MCP 时使用的 PATH 中包含 `java`。 |
| ClassNotFoundException（驱动类） | JDBC 驱动 JAR 不在 classpath | 将驱动 JAR（如 `ojdbc11.jar`）放入 `db_mcp/lib/`，并使用 `-cp` 包含 `.../db_mcp/target/*-fat.jar` 与 `.../db_mcp/lib/*`（Windows 用 `;`，Linux/macOS 用 `:`）。不要单独使用 `-jar`。 |
| 报错找不到 config | 未找到配置文件 | 在 MCP 的 `env` 中设置 `DB_MCP_CONFIG` 为 `config.yaml` 的 **绝对路径**。 |
| 连接不可用 / 快速失败 | 数据库不可达或宕机 | 检查数据库与网络后，再次调用 **list_connections** 重新校验；只有该工具会清除不可用状态。 |
| 客户端中看不到数据库工具 | MCP 未加载或路径错误 | 检查客户端 MCP 配置中的 `command` 和 `args`（路径、classpath 分隔符），确认 fat JAR 与 `lib/*` 正确，并重启客户端。 |
