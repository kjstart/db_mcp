<a id="english"></a>
[English](#english) | [中文](#chinese)

# Cursor Database MCP Server (Java 11 + JDBC)

MCP (Model Context Protocol) database server that **provides database connectivity for Cursor and other MCP-compatible products**. Built on **Java 11** and **JDBC**: any database with a JDBC driver can be used (Oracle, MySQL, PostgreSQL, SQL Server, etc.). The server uses **Alibaba Druid** for connection pooling and SQL parsing; it relies on standard `java.sql.*` and does not call `DriverManager` directly. Add the driver JAR to the classpath (or `pom.xml`) and set `driver` and `url` in config to run.

**Author:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**Project:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

## Features

- **list_connections** — List configured database connections and their availability. Each call re-checks connections; previously failed ones are retried. Use the returned names as the `connection` argument in other tools.
- **execute_sql** — Execute SQL on the chosen connection (multi-statement supported, semicolon-separated). Params: `sql`, optional `connection`.
- **execute_sql_file** — Read SQL from a file, analyze, show a confirmation window when required, then execute. **Callers must use an absolute path** for `file_path`. Trailing SQL*Plus `/`-only lines are stripped.
- **query_to_csv_file** — Run a query and write the result to a file as CSV (header + rows). Params: `sql`, `file_path` (absolute), optional `connection`.
- **query_to_text_file** — Run a query and write the result to a file as plain text (tab-separated columns per line). Params: `sql`, `file_path` (absolute), optional `connection`.

**Review and safety**

- **always_review_ddl** — When `true`, all DDL (CREATE/ALTER/DROP etc.) triggers a confirmation window before execution.
- **Danger keywords** — Two match modes: **whole_text** (substring anywhere in SQL, last gate) and **command_match** (only when the AST identifies the statement as that command). Confirmation window: Windows uses PowerShell WinForms (scrollable HTML); macOS uses osascript.

**Logging**

- **audit_log** — Optional; writes each execution to a log file (e.g. 10MB rotation).
- **mcp_console_log** — Optional; prints a short line to stderr per execute_sql / execute_sql_file (throttled).

**Connection failures**

- On connection/IO errors (e.g. connection reset), the server marks that connection as unavailable and returns a clear message. Subsequent calls to that connection fast-fail until the user checks the database and calls **list_connections** again; only **list_connections** re-validates and can clear the unavailable state.

## Configuration

Copy `config.yaml.example` to `config.yaml` and edit:

```yaml
connections:
  - name: database1
    driver: oracle.jdbc.OracleDriver
    url: "jdbc:oracle:thin:@//host:1521/ORCL"
    user: myuser
    password: mypass
  - name: database2
    driver: com.mysql.cj.jdbc.Driver
    url: "jdbc:mysql://localhost:3306/mydb"
    user: root
    password: secret
```

- **driver** — Full JDBC driver class name (put the JAR on the classpath).
- **url** — JDBC URL.
- **user** / **password** — Optional if encoded in the URL.
- **db_type** (optional) — Database type for SQL parsing/formatting (Druid `DbType` name, lower case). Default `mysql` if omitted. See **db_type reference** below.

Set the environment variable `DB_MCP_CONFIG` to the absolute path of the config file to override the default location.

### db_type reference (Druid DbType)

Use the **Config value** (lower case) in config. Full source: [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java).

**Supported db_type by category** — config value in config is the **db_type** (lower case). Examples of well-known ones:

| Category | Examples (full name → db_type) |
|----------|--------------------------------|
| **Common RDBMS** | Oracle (`oracle`), MySQL (`mysql`), MariaDB (`mariadb`), PostgreSQL (`postgresql`), SQL Server (`sqlserver`), DB2 (`db2`), H2 (`h2`), SQLite (`sqlite`), Sybase (`sybase`) |
| **Chinese Database** | DaMeng 达梦 (`dm`), Kingbase 人大金仓 (`kingbase`), Gbase (`gbase`), Oscar 神州通用 (`oscar`) |
| **Cloud / Managed** | OceanBase (`oceanbase`, `oceanbase_oracle`), PolarDB (`polardb`, `polardb2`, `polardbx`), Snowflake (`snowflake`), BigQuery (`bigquery`), Redshift (`redshift`), Athena (`athena`), Databricks (`databricks`), Azure Synapse (`synapse`) |
| **MPP / Analytics** | Greenplum (`greenplum`), GaussDB (`gaussdb`), ClickHouse (`clickhouse`), Doris (`doris`), StarRocks (`starrocks`), Presto (`presto`), Trino (`trino`) |
| **Big Data** | Hive (`hive`), HBase (`hbase`), TiDB (`tidb`), Spark (`spark`), Teradata (`teradata`) |

More types (e.g. `elastic_search`, `odps`, `mock`, `other`) — see [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java).

### Review and logging (optional)

Omit `review` / `logging` to disable. Example:

```yaml
review:
  whole_text_match:
    - truncate
    - delete
    - drop
    - call
    - execute immediate
    - alter
    - grant
    - revoke
  command_match:
    - create
    - update
    - replace
    - insert
    - merge
  always_review_ddl: true

logging:
  audit_log: true
  mcp_console_log: true
  log_file: "audit.log"   # relative to config file dir if not absolute
```

## Build and run

- **Java 11+**, **Maven 3.x**

```bash
cd db_mcp
mvn compile
mvn exec:java -Dexec.mainClass="com.alvinliu.dbmcp.DBMCPServer"
```

Package a fat JAR:

```bash
mvn package
```

The fat JAR includes the project plus snakeyaml, gson, etc.; it does **not** include JDBC drivers. Put the fat JAR in `db_mcp/target/` (from `mvn package`) and driver JAR(s) in `db_mcp/lib/`, and start with `-cp` (see below). The JVM ignores `-cp` when using `-jar`, so you must use **`-cp` + main class** to put both the fat JAR and `lib/*` on the classpath.

## Cursor MCP setup

The server talks to Cursor over **stdio**. Add a **command**-type MCP server in Cursor.

**Config file**

- Project-only: create `.cursor/mcp.json` in the project root.
- User-wide: edit `~/.cursor/mcp.json` (Windows: `%USERPROFILE%\.cursor\mcp.json`).

**Example (Windows)** — classpath separator `;`:

```json
{
  "mcpServers": {
    "db-mcp": {
      "command": "java",
      "args": [
        "-cp",
        "D:/path/to/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar;D:/path/to/db_mcp/lib/*",
        "com.alvinliu.dbmcp.DBMCPServer"
      ],
      "env": {
        "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
      }
    }
  }
}
```

**Example (Linux / macOS)** — classpath separator `:`:

```json
{
  "mcpServers": {
    "db-mcp": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar:/path/to/db_mcp/lib/*",
        "com.alvinliu.dbmcp.DBMCPServer"
      ],
      "env": {
        "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
      }
    }
  }
}
```

Replace paths with your actual paths. After saving `mcp.json`, **fully quit and reopen Cursor** so the MCP reloads. You can then use `list_connections`, `execute_sql`, `execute_sql_file`, `query_to_csv_file`, and `query_to_text_file` in Cursor.

**Driver JARs** — Put your database driver JAR(s) (e.g. Oracle `ojdbc11.jar`) in `db_mcp/lib/`. The project does not ship drivers.

## Analyzer and formatter (Druid)

- **Parsing and formatting** — Alibaba Druid; `db_type` in each connection config selects the dialect (`mysql`, `oracle`, `postgresql`, `sql_server`, etc.).
- **Analyzer** (`DruidSqlAnalyzer`) — Druid parse, statement type, **whole_text** substring match (last gate), **command_match** when the AST says it’s that command.
- **Formatter** (`DruidSqlFormatter`) — `SQLUtils.toSQLString(..., dbType)`; falls back to `BaseFormatter` on parse failure. HTML highlighting via `BaseFormatter.formatHtml`.

---
<a id="chinese"></a>
[English](#english) | [中文](#chinese)

# Cursor Database MCP Server（Java 11 + JDBC）

为 **Cursor** 等支持 MCP 的产品提供**数据库连接能力**的 MCP（Model Context Protocol）数据库服务端。基于 **Java 11** 和 **JDBC**：任意提供 JDBC 驱动的数据库均可接入（Oracle、MySQL、PostgreSQL、SQL Server 等）。主程序使用 **Alibaba Druid** 作为连接池与 SQL 解析，不直接调用 `DriverManager`；将驱动 JAR 放入 classpath（或 `pom.xml`），在配置中设置 `driver` 和 `url` 即可运行。

**作者:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**项目地址:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)


## 功能

- **list_connections** — 列出配置中的数据库连接及可用性。每次调用会重新检查连接，对之前失败的连接会重试。将返回的名称作为其他工具中的 `connection` 参数使用。
- **execute_sql** — 在指定连接上执行 SQL（支持多语句，分号分隔）。参数：`sql`，可选 `connection`。
- **execute_sql_file** — 从文件读取 SQL，分析后按需弹确认窗口，通过后执行。**调用方请对 `file_path` 使用绝对路径**。支持去除末尾仅含 `/` 的 SQL*Plus 行。
- **query_to_csv_file** — 执行查询并将结果以 CSV（表头 + 行）写入文件。参数：`sql`、`file_path`（绝对路径）、可选 `connection`。
- **query_to_text_file** — 执行查询并将结果以纯文本（每行制表符分隔列）写入文件。参数：`sql`、`file_path`（绝对路径）、可选 `connection`。

**审查与安全**

- **always_review_ddl** — 为 `true` 时，所有 DDL（CREATE/ALTER/DROP 等）执行前会弹出确认窗口。
- **危险词** — 两种匹配方式：**whole_text**（SQL 中任意子串，最后一道关）、**command_match**（仅当 AST 判定为该命令时）。确认窗口：Windows 使用 PowerShell WinForms（可滚动 HTML）；macOS 使用 osascript。

**日志**

- **audit_log** — 可选；将每次执行写入日志文件（如 10MB 轮转）。
- **mcp_console_log** — 可选；每笔 execute_sql / execute_sql_file 在 stderr 打一行简短日志（节流）。

**连接失败**

- 发生连接/IO 错误（如 connection reset）时，服务端会将该连接标记为不可用并返回明确提示。之后对该连接的调用会快速失败，直到用户确认数据库可用并再次调用 **list_connections**；只有 **list_connections** 会重新校验并可能清除不可用状态。

## 配置

将 `config.yaml.example` 复制为 `config.yaml` 并编辑：

```yaml
connections:
  - name: database1
    driver: oracle.jdbc.OracleDriver
    url: "jdbc:oracle:thin:@//host:1521/ORCL"
    user: myuser
    password: mypass
  - name: database2
    driver: com.mysql.cj.jdbc.Driver
    url: "jdbc:mysql://localhost:3306/mydb"
    user: root
    password: secret
```

- **driver** — JDBC 驱动完整类名（将对应 JAR 加入 classpath）。
- **url** — JDBC URL。
- **user** / **password** — 若 URL 中已包含可省略。
- **db_type**（可选）— 用于 SQL 解析与格式化的数据库类型（Druid `DbType` 名，小写）。不写时默认 `mysql`。见下方 **db_type 对照**。

可通过环境变量 `DB_MCP_CONFIG` 指定配置文件的绝对路径。

### db_type 对照（Druid DbType）

配置中填写**配置值**（小写）。完整枚举见 [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java)。

**按分类支持的主要 db_type**（配置里填小写的 db_type）。仅列常见/知名数据库示例：

| 分类 | 示例（全名 → db_type） |
|------|------------------------|
| **常用关系型** | Oracle (`oracle`)、MySQL (`mysql`)、MariaDB (`mariadb`)、PostgreSQL (`postgresql`)、SQL Server (`sqlserver`)、DB2 (`db2`)、H2 (`h2`)、SQLite (`sqlite`)、Sybase (`sybase`) |
| **国产数据库** | 达梦 DaMeng (`dm`)、人大金仓 Kingbase (`kingbase`)、Gbase (`gbase`)、神州通用 Oscar (`oscar`) |
| **云 / 托管** | OceanBase (`oceanbase`、`oceanbase_oracle`)、PolarDB (`polardb`、`polardb2`、`polardbx`)、Snowflake (`snowflake`)、BigQuery (`bigquery`)、Redshift (`redshift`)、Athena (`athena`)、Databricks (`databricks`)、Azure Synapse (`synapse`) |
| **MPP / 分析** | Greenplum (`greenplum`)、GaussDB (`gaussdb`)、ClickHouse (`clickhouse`)、Doris (`doris`)、StarRocks (`starrocks`)、Presto (`presto`)、Trino (`trino`) |
| **大数据** | Hive (`hive`)、HBase (`hbase`)、TiDB (`tidb`)、Spark (`spark`)、Teradata (`teradata`) |

更多类型（如 `elastic_search`、`odps`、`mock`、`other`）见 [Druid DbType](https://github.com/alibaba/druid/blob/master/src/main/java/com/alibaba/druid/DbType.java)。

### 审查与日志（可选）

不配置 `review` / `logging` 即不启用。示例：

```yaml
review:
  whole_text_match:
    - truncate
    - delete
    - drop
    - call
    - execute immediate
    - alter
    - grant
    - revoke
  command_match:
    - create
    - update
    - replace
    - insert
    - merge
  always_review_ddl: true

logging:
  audit_log: true
  mcp_console_log: true
  log_file: "audit.log"   # 相对路径基于配置文件所在目录
```

## 编译与运行

- **Java 11+**、**Maven 3.x**

```bash
cd db_mcp
mvn compile
mvn exec:java -Dexec.mainClass="com.alvinliu.dbmcp.DBMCPServer"
```

打可执行 JAR：

```bash
mvn package
```

Fat JAR 含本工程及 snakeyaml、gson 等，**不含** JDBC 驱动。将 fat JAR 放在 `db_mcp/target/`（由 `mvn package` 生成），驱动 JAR 放在 `db_mcp/lib/`，并用 `-cp` 启动（见下）。JVM 规定使用 `-jar` 时会忽略 `-cp`，因此需用 **`-cp` + 主类** 方式，把 fat JAR 与 `lib/*` 都放进 classpath。

## 在 Cursor 中配置 MCP

服务通过 **stdio** 与 Cursor 通信。在 Cursor 的 MCP 配置中增加一条「命令」型服务器。

**配置文件位置**

- 仅当前项目：在项目根目录创建 `.cursor/mcp.json`。
- 本机全局：编辑 `~/.cursor/mcp.json`（Windows：`%USERPROFILE%\.cursor\mcp.json`）。

**示例（Windows）** — classpath 分隔符 `;`：

```json
{
  "mcpServers": {
    "db-mcp": {
      "command": "java",
      "args": [
        "-cp",
        "D:/path/to/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar;D:/path/to/db_mcp/lib/*",
        "com.alvinliu.dbmcp.DBMCPServer"
      ],
      "env": {
        "DB_MCP_CONFIG": "D:/path/to/db_mcp/config.yaml"
      }
    }
  }
}
```

**示例（Linux / macOS）** — classpath 分隔符 `:`：

```json
{
  "mcpServers": {
    "db-mcp": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/db_mcp/target/db-mcp-1.0.0-SNAPSHOT-fat.jar:/path/to/db_mcp/lib/*",
        "com.alvinliu.dbmcp.DBMCPServer"
      ],
      "env": {
        "DB_MCP_CONFIG": "/path/to/db_mcp/config.yaml"
      }
    }
  }
}
```

将路径改为本机实际路径。保存 `mcp.json` 后**完全退出并重新打开 Cursor**，MCP 才会重新加载。之后可在 Cursor 中使用 `list_connections`、`execute_sql`、`execute_sql_file`、`query_to_csv_file`、`query_to_text_file`。

**驱动 JAR** — 将所用数据库的驱动 JAR（如 Oracle `ojdbc11.jar`）放入 `db_mcp/lib/`。本工程不随包发布驱动。

## 分析器与格式化（Druid）

- **解析与格式化** — 使用 Alibaba Druid；各连接配置中的 `db_type` 选择方言（`mysql`、`oracle`、`postgresql`、`sql_server` 等）。
- **分析器**（`DruidSqlAnalyzer`）— Druid 解析、语句类型、**whole_text** 子串匹配（最后一道关）、**command_match** 仅当 AST 判定为该命令时匹配。
- **格式化**（`DruidSqlFormatter`）— `SQLUtils.toSQLString(..., dbType)`；解析失败时回退到 `BaseFormatter`。HTML 高亮通过 `BaseFormatter.formatHtml`。
