# 部署指南

## 配置文件
`application.yaml` 已加入 `.gitignore`，不会提交到仓库。首次使用请复制模板并填入自己的密钥：

```bash
cp knowhub-app/src/main/resources/application.yaml.example knowhub-app/src/main/resources/application.yaml
```

## 本地依赖
使用以下命令启动本地依赖：

```bash
docker compose up -d
```

compose 服务栈包含 MySQL、Redis、Kafka、PostgreSQL（pgvector）、Elasticsearch、MinIO、Milvus、Attu 和 Neo4j。

> 注意：MySQL 使用本地 3306 端口，如果本地已安装 MySQL 会端口冲突。可修改 `docker-compose.yml` 中的端口映射。

## 后端
```bash
cd knowhub-app
mvn spring-boot:run
```

后端默认运行在 `9082` 端口。

## 前端
```bash
cd vue
npm ci
npm run dev
```

Vite 将 `/api/v1` 代理到 `http://127.0.0.1:9082`。

## 环境变量
以下环境变量需在 `application.yaml` 中配置（或通过系统环境变量注入）：

| 变量 | 用途 | 是否必需 |
|------|------|----------|
| `MIMO_API_KEY` | MIMO 对话模型密钥 | 必需 |
| `ALI_BAI_LIAN_API_KEY` | 百炼 embedding 接入 | 必需 |
| `TAVILY_API_KEY` | Tavily 联网搜索 | 可选 |
| `KNOWHUB_ADMIN_PASSWORD` | 管理员密码，默认 `admin123456` | 可选 |
| `ELASTICSEARCH_PASSWORD` | Elasticsearch 密码，默认 `elastic` | 可选 |
| `NEO4J_PASSWORD` | Neo4j 密码，默认 `12345678` | 可选 |

## 存储与搜索
默认的文档索引将向量写入 PostgreSQL/pgvector，关键词写入 Elasticsearch。Milvus 作为可选的向量存储方案保留，在本地 compose 中仍然可用。Neo4j 保留用于文档结构图谱投影。
