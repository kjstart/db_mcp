<a id="english"></a>
[English](#english) | [中文](#chinese)

# db_mcp — Database MCP Server for AI (Java 11 + JDBC)

<img src="https://www.alvinliu.com/wp-content/uploads/2026/03/cursor_db_mcp_poster2.png" alt="logo" width="360" />

A **general-purpose MCP server** that lets **any MCP-compatible AI client** (Cursor, Claude Code, OpenClaw, etc.) connect to **any JDBC-supported database** (Oracle, MySQL, PostgreSQL, SQL Server, and more). Not tied to a single IDE or tool.

**Built-in safety** — Optionally asks for human confirmation before running risky SQL, helping prevent accidental data loss.

Built on **MCP (Model Context Protocol)** with **Java 11 and JDBC**. Add the JDBC driver JAR, configure your database in `config.yaml`, and attach the server to your AI client. See **USER_GUIDE.md** for step-by-step setup (Cursor, Claude Code, OpenClaw, and others).

**Author:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**Project:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

**Demos:** [YouTube](https://www.youtube.com/watch?v=kySJBv6dPEo) | [Bilibili 中文](https://www.bilibili.com/video/BV127fKB3EuR/)

## Features

- **list_connections** — List configured database connections and their availability (`db_type`, etc.).
- **execute_sql** — Execute SQL (multi-statement), call stored procedures/functions (JDBC `{ call }`), and on Oracle run anonymous PL/SQL blocks (`BEGIN...END`).
- **execute_sql_file** — Run SQL from a file (same rules as execute_sql).
- **query_to_csv_file** — Run a query and write the result to a CSV file.
- **query_to_text_file** — Run a query and write the result to a text file (e.g. procedure source).

**Review and safety** — When enabled in config, some SQL may require user approval before execution; if rejected, the client receives an execution-cancelled result.

<img src="https://www.alvinliu.com/wp-content/uploads/2026/03/db_mcp_color_bar.png" alt="db_mcp confirmation window" />

**Schema protection** — AI cannot reference objects in other schemas using qualified names (e.g. `hr.employees`). All SQL must use objects belonging to the connected user's own schema.

**Logging** — Optional audit log (file rotation) and stderr console log.

<img src="https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_audit_log.png" alt="db_mcp audit log" />

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

On first startup, the server automatically encrypts the plain-text `url`, `user`, and `password` values in `config.yaml`. Subsequent startups read and decrypt them transparently — no manual steps needed.
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

## Attach to your AI client (MCP)

The server communicates over **stdio**. How you add it depends on your client:

- **Cursor** — Add a command-type MCP in Cursor settings; see **USER_GUIDE.md** for `mcp.json` examples (Windows and Linux/macOS).
- **Claude Code** — Use `claude mcp add ...`; see **USER_GUIDE.md** for the exact command and path placeholders.
- **Claude Code for VS Code (VS Code version)** — Create a `.mcp.json` file in your project directory; see **USER_GUIDE.md** for configuration examples (Windows and Linux/macOS).
- **OpenClaw** — Add the MCP server by sending a conversation message; see **USER_GUIDE.md** for the exact text and path placeholders.

Replace path placeholders with your actual install path and config file. **Driver JARs** go in `db_mcp/lib/` (e.g. Oracle `ojdbc11.jar`); the project does not bundle drivers.

---
<a id="chinese"></a>
[English](#english) | [中文](#chinese)

# db_mcp — 面向 AI 的通用数据库 MCP 服务（Java 11 + JDBC）

面向 **任意支持 MCP 的 AI 客户端**（Cursor、Claude Code 等）连接 **任意支持 JDBC 的数据库**（Oracle、MySQL、PostgreSQL、SQL Server 等），不绑定某一款 IDE 或工具。

**可选安全确认** — 在配置中开启后，部分 SQL 执行前会要求用户确认；若用户拒绝，客户端将收到执行已取消的结果。

基于 **MCP（Model Context Protocol）**，使用 **Java 11 + JDBC**。将 JDBC 驱动 JAR 加入 classpath、在 `config.yaml` 中配置数据库后，把本服务挂到你的 AI 客户端即可。详细步骤见 **USER_GUIDE.md**（含 Cursor、Claude Code 等）。

**作者:** Alvin Liu [https://alvinliu.com](https://alvinliu.com)

**项目地址:** [https://github.com/kjstart/cursor_db_mcp](https://github.com/kjstart/cursor_db_mcp)

**演示:** [YouTube](https://www.youtube.com/watch?v=kySJBv6dPEo) | [B站中文](https://www.bilibili.com/video/BV127fKB3EuR/)

## 功能

- **list_connections** — 列出已配置连接及可用性（含 db_type 等）。
- **execute_sql** — 执行 SQL（支持多语句）、调用存储过程/函数（JDBC `{ call }`），Oracle 下还可执行匿名块（`BEGIN...END`）。
- **execute_sql_file** — 从文件执行 SQL，规则同 execute_sql。
- **query_to_csv_file** — 执行查询并写入 CSV 文件。
- **query_to_text_file** — 执行查询并写入纯文本（如存储过程源码）。

**审查与安全** — 在配置中启用后，部分 SQL 需用户确认后才执行；若用户拒绝，客户端会收到执行已取消。

<img src="https://www.alvinliu.com/wp-content/uploads/2026/03/db_mcp_color_bar.png" alt="db_mcp confirmation window" />

**Schema 保护** — AI 不能通过 schema 限定名（如 `hr.employees`）访问其他 schema 的对象，只能操作当前连接用户自己 schema 下的对象。

**日志** — 可选审计日志（按文件轮转）和 stderr 控制台日志。

<img src="https://www.alvinliu.com/wp-content/uploads/2026/02/db_mcp_audit_log.png" alt="db_mcp 审计日志"/>

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

首次启动时，服务端会自动将 `config.yaml` 中的明文 `url`、`user`、`password` 加密写回，后续启动透明解密，无需手动操作。
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

## 挂载到 AI 客户端（MCP）

服务通过 **stdio** 与客户端通信。按你使用的客户端配置：

- **Cursor** — 在 Cursor 设置中添加「命令」型 MCP，详见 **USER_GUIDE.md** 中的 `mcp.json` 示例（Windows / Linux / macOS）。
- **Claude Code** — 使用 `claude mcp add ...`，详见 **USER_GUIDE.md** 中的命令与路径占位说明。
- **Claude Code for VS Code (VS Code 版本)** — 在项目目录下创建 `.mcp.json` 文件，详见 **USER_GUIDE.md** 中的配置说明（Windows / Linux / macOS）。
- **OpenClaw** — 通过发送对话消息添加 MCP 服务，详见 **USER_GUIDE.md** 中的示例文案与路径占位。

将文档中的路径占位替换为本机实际路径。**驱动 JAR** 放入 `db_mcp/lib/`（如 Oracle `ojdbc11.jar`），本工程不随包发布驱动。
