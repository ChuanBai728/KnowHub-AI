# API 版本管理

所有 HTTP API 使用 `/api/v1` 作为公共路径前缀。

后端路由前缀集中在 `ai.knowhub.web.ApiVersion` 中管理。

## 当前路由
- `/api/v1/chat`
- `/api/v1/admin/auth`
- `/api/v1/manage/document`
- `/api/v1/manage/knowledge`

## 前端
前端必须通过 `vue/src/api/api.js` 中的 `API_VERSION_PREFIX` 构建 API 路径。开发环境下，Vite 将 `/api/v1` 代理到后端服务。

## 添加新 API
在 `/api/v1/<domain>` 下添加新控制器。除 Spring Boot 管理的健康检查/actuator 端点外，避免使用未加版本的路由。

当需要进行破坏性 API 变更时，引入 `/api/v2` 并行运行，保持 `/api/v1` 稳定直到客户端完成迁移。
