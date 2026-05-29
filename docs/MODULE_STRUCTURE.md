# 模块结构

KnowHub AI 采用扁平的 Maven 模块布局。

## 依赖方向
`knowhub-app` 依赖各功能模块，功能模块依赖 `knowhub-common`，部分模块还依赖 `knowhub-infra`。

```text
knowhub-app
  -> knowhub-auth
  -> knowhub-chat
      -> knowhub-document
  -> knowhub-document
  -> knowhub-infra
  -> knowhub-common
```

## 模块职责
- `knowhub-common`：共享响应对象、异常、枚举、MyBatis 辅助工具、OpenAPI 配置。
- `knowhub-infra`：Redis、Redisson、分布式锁/租约辅助工具、百度 UID。
- `knowhub-auth`：管理员认证、JWT 解析、预览模式写保护。
- `knowhub-document`：文档摄入、对象存储、异步 Kafka 任务、分块、向量/全文/图谱持久化。
- `knowhub-chat`：会话管理、流式对话、RAG 编排、追踪和基准输出。
- `knowhub-app`：启动引导和运行时配置。

## 命名规范
- `Dto` 表示请求输入。
- `Vo` 表示后端输出。
- `View` 保留给 Vue 页面组件使用。
