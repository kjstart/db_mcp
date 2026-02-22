<a id="english"></a>
[English](#english) | [中文](#chinese)

# Cursor Database MCP Server (Java 11 + JDBC)

Allows **Cursor** and **other MCP-compatible tools** to connect to **any JDBC-supported database** (Oracle, MySQL, PostgreSQL, SQL Server, and more).

Includes **built-in safety checks** that ask for confirmation before running potentially dangerous SQL, helping prevent accidental data loss.

Built on **MCP (Model Context Protocol)** using **Java 11 and JDBC**. Just add the JDBC driver JAR and configure your database connection to get started.

**Author:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**Project:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

## Features

- **list_connections** — List configured database connections and their availability.
- **execute_sql** — Execute SQL query and simple procedures.
- **execute_sql_file** — Read SQL from a file to run complex SQL.
- **query_to_csv_file** — Run a query and write the result to a file as CSV, for larger result sets.
- **query_to_text_file** — Run a query and write the result to a file as plain text (for AI to read stored procedures).

**Review and safety** — Dangerous SQL or DDL can trigger a confirmation window before execution.

![db_mcp confirmation window](https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_confirmation_window.png)

**Logging** — Optional audit log (file rotation) and stderr console log.

![db_mcp audit log](https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_audit_log.png)

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

Replace paths with your actual paths. After saving `mcp.json`, **fully quit and reopen Cursor** so the MCP reloads. Cursor can then call these tools when you chat (e.g. `list_connections`, `execute_sql`, `execute_sql_file`, `query_to_csv_file`, `query_to_text_file`).

**Driver JARs** — Put your database driver JAR(s) (e.g. Oracle `ojdbc11.jar`) in `db_mcp/lib/`. The project does not ship drivers.

---
<a id="chinese"></a>
[English](#english) | [中文](#chinese)

# Cursor Database MCP Server（Java 11 + JDBC）

支持 **Cursor** 及其他 **MCP 兼容工具** 连接任何 **支持 JDBC 的数据库**，包括 Oracle、MySQL、PostgreSQL、SQL Server 等，无需针对不同数据库做额外适配。

**内置 SQL 安全校验机制**，在执行可能存在风险的 SQL 操作前会主动请求确认，帮助避免误操作导致的数据丢失。

基于 **MCP（Model Context Protocol）** 构建，使用 **Java 11 + JDBC** 实现。只需将对应的 JDBC Driver JAR 加入运行环境，并配置数据库连接即可使用。

**作者:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**项目地址:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)


## 功能

- **list_connections** — 列出配置的数据库连接及可用性。
- **execute_sql** — 执行 SQL 或简单存储过程。
- **execute_sql_file** — 从文件读取并执行 SQL，适合较长脚本。
- **query_to_csv_file** — 执行查询并将结果写入 CSV 文件，适合大量数据。
- **query_to_text_file** — 执行查询并将结果写入纯文本，便于 AI 阅读（如存储过程源码）。

**审查与安全** — 危险 SQL 或 DDL 执行前会弹确认窗口。

![db_mcp 确认窗口](https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_confirmation_window.png)

**日志** — 可选审计日志（按文件轮转）和 stderr 控制台日志。

![db_mcp 审计日志](https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_audit_log.png)

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

将路径改为本机实际路径。保存 `mcp.json` 后**完全退出并重新打开 Cursor**，MCP 才会重新加载。之后 Cursor 在对话时即可调用这些工具（如 `list_connections`、`execute_sql`、`execute_sql_file`、`query_to_csv_file`、`query_to_text_file`）。

**驱动 JAR** — 将所用数据库的驱动 JAR（如 Oracle `ojdbc11.jar`）放入 `db_mcp/lib/`。本工程不随包发布驱动。