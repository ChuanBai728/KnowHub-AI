# CLAUDE.md

本文件为 KnowHub AI 提供编码代理指导。

## 项目概述
KnowHub AI 是核心知识库问答项目，使用 Maven groupId `ai.knowhub`。

后端技术栈：Java 17、Spring Boot 3.5、Spring AI 1.1。前端：Vue 3 + Vite。

## 模块一览
- `knowhub-common`：共享响应封装、异常、枚举、OpenAPI/MyBatis 辅助工具。
- `knowhub-infra`：Redisson 基础设施、锁/租约工具、百度 UID 自动配置。
- `knowhub-auth`：管理员登录、JWT 令牌验证、预览模式拦截器。
- `knowhub-document`：文档生命周期、MinIO、Kafka 任务、分块、PGVector、Elasticsearch、Milvus 配置和 Neo4j 结构图谱投影。
- `knowhub-chat`：对话 API、SSE 流式响应、RAG 规划/检索、Agent 执行器、追踪视图。
- `knowhub-app`：可运行应用及 `application.yaml`。
- `vue`：前端应用。

## 配置文件
`application.yaml` 已加入 `.gitignore`。首次使用需从 `application.yaml.example` 复制并填入密钥。

## 常用命令
```bash
mvn clean install                    # 构建全部模块
mvn test                             # 运行测试
cd knowhub-app && mvn spring-boot:run  # 启动后端（9082 端口）
cd vue && npm run dev                # 启动前端（5173 端口）
docker compose up -d                 # 启动本地依赖
cd vue && npm run build              # 构建前端
```

## API 规则
所有后端 API 必须使用 `/api/v1` 版本前缀。

当前路由根路径：
- `/api/v1/chat`
- `/api/v1/admin/auth`
- `/api/v1/manage/document`
- `/api/v1/manage/knowledge`

前端必须通过 `vue/src/api/api.js` 中的 `API_VERSION_PREFIX` 构建 API 路径。

## 命名规则
- `Dto`：请求参数或命令对象。
- `Vo`：后端响应值。
- `View`：仅用于 Vue 页面组件，如 `AdminLayoutView.vue`。

不要创建新的后端 `*View` 响应类。

## ID 生成
使用百度 UID。项目封装代码位于 `ai.knowhub.infra.uid`，上游核心代码位于 `com.baidu.fsg.uid`。

## 技术方案
MySQL、PostgreSQL pgvector、Elasticsearch、Redis、Kafka、MinIO、Milvus 和 Neo4j。Milvus 和 Neo4j 为可选项，不要移除它们的配置、compose 服务或枚举值。

## 验证
开发时使用模块级验证，环境允许时在交接前运行完整测试套件：
```bash
mvn test
mvn -pl knowhub-app -am test
cd vue && npm run build
```
