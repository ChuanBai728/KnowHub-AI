# 仓库开发指南

## 项目结构与模块组织
KnowHub AI 是一个 Maven 多模块 Java 17 / Spring Boot 3.5 项目，前端使用独立的 Vue 应用。后端模块如下：

- `knowhub-common`：共享 API 响应封装、异常、枚举、Web/MyBatis 辅助工具。
- `knowhub-infra`：Redis/Redisson 基础设施、分布式锁、租约、百度 UID 集成。
- `knowhub-auth`：管理员认证、JWT、预览模式写保护。
- `knowhub-document`：文档上传、解析、分块、索引、检索存储、MinIO、Kafka、PGVector、Elasticsearch、Neo4j、Milvus 配置。
- `knowhub-chat`：对话、RAG 编排、Agent 执行、检索观察和 SSE 响应。
- `knowhub-app`：可执行的 Spring Boot 应用及运行时配置。

前端应用位于 `vue/` 目录。数据库脚本位于 `sql/Mysql/` 和 `sql/PostgresSql/`。新增文档位于 `docs/` 目录。

## 构建、测试与开发命令
- `mvn clean install`：构建所有后端模块。
- `mvn test`：运行所有 Java 测试。
- `cd knowhub-app && mvn spring-boot:run`：在 `9082` 端口启动后端服务。
- `docker compose up -d`：启动本地依赖：MySQL、Redis、Kafka、PostgreSQL/pgvector、Elasticsearch、MinIO、Milvus、Attu 和 Neo4j。
- `cd vue && npm ci`：安装前端依赖。
- `cd vue && npm run dev`：在 `5173` 端口启动 Vite 开发服务器，将 `/api/v1` 代理到 `http://127.0.0.1:9082`。
- `cd vue && npm run build`：构建生产环境前端。

## 代码风格与命名规范
使用 UTF-8 编码和 Java 17。Java 包必须在 `ai.knowhub` 下，百度 UID 核心类位于 `com.baidu.fsg.uid`。类名使用 PascalCase，方法和字段使用 camelCase，常量使用大写蛇形命名。

命名规则：
- `*Dto`：请求参数或命令对象。
- `*Vo`：后端 API 响应值。
- `*View.vue`：仅用于前端页面组件。

REST 控制器保持在 `/api/v1/...` 路径下。

## 测试指南
将 Java 测试放在各模块的 `src/test/java` 目录下，命名为 `*Test.java` 或 `*Tests.java`。优先使用 JUnit 5、Mockito 和 MockMvc 编写聚焦的控制器/服务测试。使用 `npm run build` 作为前端最低验证。

## 安全与配置提示
`application.yaml` 已加入 `.gitignore`，不提交真实密钥。首次使用请从 `application.yaml.example` 复制并填入自己的密钥。

需要配置的密钥：`MIMO_API_KEY`（对话模型）、`ALI_BAI_LIAN_API_KEY`（embedding）、`TAVILY_API_KEY`（搜索，可选）。

确保生成文件（如 `target/`、`logs/`、`.flattened-pom.xml`、`node_modules/`、`.playwright-cli/` 和 `output/`）不被提交。
