# 重庆旅游线路规划系统

## 🛠 技术栈
- 前端：React + TypeScript + Tailwind + Ant Design + React-Leaflet（Vite）
- 后端：Java 17 + Maven + 内置 HTTP Server + Hibernate ORM
- 数据库：PostgreSQL 15

## 📦 数据文件
- `backend/src/main/resources/nodes.csv`：景点节点（必需）
- `backend/src/main/resources/edges.csv`：边数据（可选，存在则优先使用；否则按地理距离自动生成边）

`edges.csv` 格式：至少包含表头 `from,to`；可选第三列 `distance_meters`（若缺省则按两点经纬度计算球面距离）。

文档索引：见 [docs/README.md](./docs/README.md)。

## 🚀 启动指南
1. 确保 Docker Desktop 已启动
2. 在根目录执行：`docker compose up -d --build`
3. 等待容器启动完成后访问前端与后端接口

## 🔗 服务地址
- 前端：http://localhost:3514
- 后端健康检查：http://localhost:8514/api/health
- 节点列表：http://localhost:8514/api/nodes
- 数据库：localhost:5514（db: cq_travel / user: cq / pass: cq）

## 🧪 测试账号
- 无（本项目无登录鉴权）

---

## 🐳 Docker 镜像源配置

### 推荐配置（基于实际项目验证）

#### 1. Docker 镜像源
当前 `Dockerfile` 使用 `docker.m.daocloud.io` 作为镜像前缀以加速国内拉取（也可替换为官方 Docker Hub 镜像）。

#### 2. npm 依赖源
在 `frontend/Dockerfile` 中使用：`npm config set registry https://registry.npmmirror.com`

#### 3. Maven 依赖源
在 `backend` 目录下提供 `settings.xml` 并在 `backend/Dockerfile` 中使用该镜像配置。
