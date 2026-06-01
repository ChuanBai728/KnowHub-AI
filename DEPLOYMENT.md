# KnowHub AI 部署文档

## 架构总览

```
用户浏览器
  │
  ▼
公网服务器 (121.43.67.227)
  ├── Nginx :80        → 前端静态文件 (/var/www/knowhub/dist)
  │                    → /api/ 反向代理 → 127.0.0.1:8080
  ├── frps :7000       → frp 控制端口（接收 frpc 连接）
  └── frps :8080       → frp 数据端口（转发到私有服务器:9082）
        │
        ▼ (frp 隧道)
私有服务器 (100.103.165.45, Tailscale 内网)
  ├── Spring Boot :9082 → 后端 API
  ├── frpc              → frp 客户端（连接公网服务器:7000）
  └── Docker Compose    → 基础设施服务
      ├── MySQL 8.4          :3306
      ├── Redis 7.4          :6379
      ├── Kafka 3.7.0        :9092
      ├── PostgreSQL pgvector :5432
      ├── Elasticsearch 8.15 :9200
      ├── MinIO              :9000/:9001
      ├── Neo4j 5.26         :7474/:7687
      ├── Milvus (已禁用)     :19530
      ├── etcd (已禁用)
      └── Attu (已禁用)
```

## 服务器信息

### 私有服务器（应用服务器）

| 项目 | 值 |
|------|-----|
| Tailscale IP | 100.103.165.45 |
| 公网 IP | 36.154.113.105 |
| 系统 | Ubuntu 22.04 |
| 用户 | ubuntu |
| 密码 | 1209 |
| 硬盘挂载 | /dev/nvme0n1p2 (1.8T) |

### 公网服务器（中转服务器）

| 项目 | 值 |
|------|-----|
| IP | 121.43.67.227 |
| 系统 | Ubuntu |
| 用户 | root |
| 密码 | 188511Shr. |
| 内存 | 1.6G |

## 应用密码

所有服务统一使用以下密码：

| 服务 | 密码 |
|------|------|
| MySQL root | KnowHub2024! |
| PostgreSQL postgres | KnowHub2024! |
| Elasticsearch elastic | KnowHub2024! |
| Neo4j neo4j | KnowHub2024! |
| MinIO | minioadmin / minioadmin |
| frp token | KnowHub2024FRP |
| 管理员后台 | admin / KnowHub2024! |

## AI API Keys

| 服务 | Key | Base URL |
|------|-----|----------|
| 小米 MiMo | tp-csvhj47jkdb499dod23ry8pzzeohd2nle88b14qiq3abycr8 | https://token-plan-cn.xiaomimimo.com/ |
| 阿里百炼 (DashScope) | sk-04d32a54f49043589709e5732cc8184d | https://dashscope.aliyuncs.com/compatible-mode/ |
| Tavily | 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 | https://api.tavily.com |

## 关键文件路径

### 私有服务器

| 文件 | 路径 |
|------|------|
| 项目代码 | ~/SHR/KnowHub-AI/ |
| 应用配置 | ~/SHR/KnowHub-AI/knowhub-app/src/main/resources/application.yaml |
| Docker Compose | ~/SHR/KnowHub-AI/docker-compose.yml |
| frpc 配置 | ~/SHR/KnowHub-AI/frpc.ini |
| Spring Boot JAR | ~/SHR/KnowHub-AI/knowhub-app/target/knowhub-app-0.0.1-SNAPSHOT.jar |
| systemd 服务 | /etc/systemd/system/knowhub.service |
| frpc 服务 | /etc/systemd/system/frpc.service |
| 应用日志 | journalctl -u knowhub -f |

### 公网服务器

| 文件 | 路径 |
|------|------|
| Nginx 配置 | /etc/nginx/sites-available/knowhub |
| frps 配置 | /etc/frps.ini |
| frps 二进制 | /usr/local/bin/frps |
| systemd 服务 | /etc/systemd/system/frps.service |
| 前端静态文件 | /var/www/knowhub/dist/ |

## systemd 服务

### knowhub.service（私有服务器）

```ini
[Unit]
Description=KnowHub AI Application
After=network.target docker.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/SHR/KnowHub-AI
ExecStart=/usr/bin/java -Xms256m -Xmx1024m -jar knowhub-app/target/knowhub-app-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### frpc.service（私有服务器）

```ini
[Unit]
Description=frpc service
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/frpc -c /home/ubuntu/SHR/KnowHub-AI/frpc.ini
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### frps.service（公网服务器）

```ini
[Unit]
Description=frps service
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/frps -c /etc/frps.ini
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

## Nginx 配置（公网服务器）

```nginx
server {
    listen 80;
    server_name _;

    location / {
        root /var/www/knowhub/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
        proxy_read_timeout 300s;
    }
}
```

## frp 配置

### frpc.ini（私有服务器）

```ini
[common]
server_addr = 121.43.67.227
server_port = 7000
token = KnowHub2024FRP

[knowhub-http]
type = tcp
local_ip = 127.0.0.1
local_port = 9082
remote_port = 8080

[knowhub-minio]
type = tcp
local_ip = 127.0.0.1
local_port = 9000
remote_port = 8081
```

### frps.ini（公网服务器）

```ini
[common]
bind_port = 7000
token = KnowHub2024FRP
```

## 端口映射

| 公网服务器端口 | 协议 | 用途 | 访问来源 |
|--------------|------|------|---------|
| 22 | TCP | SSH | 任意 |
| 80 | TCP | HTTP（Nginx） | 任意 |
| 7000 | TCP | frps 控制端口 | 36.154.113.105 (私有服务器) |
| 8080 | TCP | frp 数据转发 | 任意（内部 frps 监听） |
| 8081 | TCP | MinIO 转发 | 任意（内部 frps 监听） |

## 常用运维命令

### 服务管理

```bash
# 私有服务器
ssh ubuntu@100.103.165.45

# 重启应用
sudo systemctl restart knowhub

# 查看应用日志
sudo journalctl -u knowhub -f

# 重启 frpc
sudo systemctl restart frpc

# 重启 Docker 服务
cd ~/SHR/KnowHub-AI && sudo docker compose restart

# 查看 Docker 容器状态
sudo docker ps
```

```bash
# 公网服务器
ssh root@121.43.67.227

# 重启 frps
systemctl restart frps

# 重启 Nginx
systemctl reload nginx

# 查看 frps 日志
journalctl -u frps -f
```

### 数据库操作

```bash
# 进入 MySQL
sudo docker exec -it knowhub-mysql mysql -u root -pKnowHub2024! knowhub_business_chat

# 进入 PostgreSQL
sudo docker exec -it knowhub-postgres psql -U postgres -d knowhub_pgvector

# 进入 Redis
sudo docker exec -it knowhub-redis redis-cli
```

### 重新构建部署

```bash
# 私有服务器上执行
cd ~/SHR/KnowHub-AI

# 拉取最新代码
git pull

# 重新构建后端
mvn clean package -DskipTests

# 重启应用
sudo systemctl restart knowhub

# 重新构建前端（在本地或服务器上）
cd vue && npm install && npm run build

# 上传前端到公网服务器
scp -r vue/dist/* root@121.43.67.227:/var/www/knowhub/dist/
```

## 防火墙配置

### 私有服务器 (ufw)

无需额外开放端口，所有服务通过 Tailscale 内网或 localhost 访问。

### 公网服务器 (ufw)

```
22/tcp    ALLOW    Anywhere
80/tcp    ALLOW    Anywhere
7000/tcp  ALLOW    Anywhere    # frps 控制端口
8080/tcp  ALLOW    Anywhere    # frp 数据转发
8081/tcp  ALLOW    Anywhere    # MinIO 转发
```

### 阿里云安全组

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 22 | 0.0.0.0/0 | SSH |
| TCP | 80 | 0.0.0.0/0 | HTTP |
| TCP | 7000 | 36.154.113.105/32 | frps（仅私有服务器） |
| TCP | 8080 | 0.0.0.0/0 | frp 数据转发 |
| TCP | 8081 | 0.0.0.0/0 | MinIO 转发 |

## 注意事项

1. **8080 端口冲突**：公网服务器上之前有一个 Java 项目占用 8080 端口（已 kill），如果该进程有 systemd 自动重启，需要禁用对应服务。
2. **healthplus Nginx 配置**：已从 sites-enabled 移除（原文件在 sites-available 中保留），避免与 knowhub 配置冲突。
3. **Docker 镜像加速**：私有服务器已配置阿里云 Docker 镜像源 (`/etc/docker/daemon.json`)。
4. **数据库初始化**：MySQL 和 PostgreSQL 的建表 SQL 位于 `sql/Mysql/` 和 `sql/PostgresSql/` 目录，首次部署需手动执行。
5. **Tailscale**：私有服务器通过 Tailscale 组网，IP 为 100.103.165.45。公网服务器未安装 Tailscale。
