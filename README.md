# 🏛️ 古代天文简仪轴承摩擦仿真与指向精度分析系统

> 用于某科技史团队元代简仪复原研究的全栈工程化系统
> 支持4轴轴承摩擦仿真、磨损预测、指向误差分析、实时告警推送

---

## 📐 系统架构图

```
                              ┌────────────────────────────────────────────────────────────┐
                              │                        前端 (Docker Nginx)             │
                              │┌─────────────────────┐   ┌──────────────────────────┐   │
                  HTTP/WS     ││  Three.js 3D模型     │   │ Canvas轴承剖面/过程纹理 │   │
◄────────────────────────────►││  (simplified_armilla │   │  (bearing_panel.js)    │   │
  80端口                      ││   _3d.js)            │   │ Chart.js趋势图表        │   │
                              │└─────────────────────┘   └──────────────────────────┘   │
                              └────────────────────────────────────────────────────────────┘
                                                        ▲
                                                        │ WebSocket STOMP
                                                        │ /topic/*
                                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                    SpringBoot 后端 V2.0 (四模块解耦架构)                               │
│                                                                                           │
│   ┌──────────────────┐    SensorDataReceivedEvent     ┌──────────────────────────────┐   │
│   │  mqtt_receiver   │ ─────────────────────────────► │  friction_simulator          │   │
│   │  模块            │   (Spring Events发布/订阅)     │  模块                        │   │
│   │  - MQTT监听入口  │                                 │  - 混合润滑+Hamrock-Dowson  │   │
│   │  - 8维数据校验器 │                                 │  - 4材料对实验K值数据库    │   │
│   └──────────────────┘                                 │  - Archard磨损计算         │   │
│            ▲                                            └──────────────┬───────────────┘   │
│            │                                                           │                   │
│            │ MQTT 1883                                           仿真完成事件              │
│            │                                                           ▼                   │
│   ┌──────────────────┐                                 ┌──────────────────────────────┐   │
│   │  Mosquitto       │◄─────────────────────────────   │  alarm_ws 模块              │   │
│   │  MQTT Broker     │      传感器数据上报             │  - 5类告警评估              │   │
│   └──────────────────┘                                 │  - STOMP 4通道推送         │   │
│            ▲                                            └──────────────┬───────────────┘   │
│            │                                                           │                   │
│            │                                                           │ 指向请求事件       │
│   ┌──────────────────┐                                 ┌──────────────────────────────┐   │
│   │  Python模拟器    │ ─────────────────────────────► │  pointing_analyzer 模块      │   │
│   │  sensor_simulator│   PointingAnalysisRequested    │  - DCM几何误差建模          │   │
│   │  (6种工况)       │                                 │  - 误差传递+不确定度评定    │   │
│   └──────────────────┘                                 └──────────────────────────────┘   │
│            │                                                                             │
│            ▼                                                                             │
│   ┌──────────────────────────────────────────────────────────────────────────────────┐   │
│   │  Actuator + Prometheus Exporter    /actuator/prometheus                       │   │
│   │  Micrometer 指标 (HTTP/JVM/HikariCP/Tomcat)                                   │   │
│   └──────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                            │
                                            │ JDBC HikariCP
                                            ▼
                              ┌─────────────────────────────────┐
                              │  PostgreSQL 16                  │
                              │  ├─ 6核心表 + JSONB字段         │
                              │  ├─ 12条BTree索引优化          │
                              │  ├─ 生产级postgresql.conf       │
                              │  └─ 2视图 + 2SQL函数           │
                              └─────────────────────────────────┘
                                            ▲
                                            │ 抓取
                              ┌─────────────┴────────────┐
                              │  Prometheus (可选监控)     │
                              └──────────────────────────┘
```

---

## ✨ 核心功能特性

### 后端能力
| 模块 | 能力 |
|------|------|
| **mqtt_receiver** | MQTT数据采集、8维度校验、设备合法性验证 |
| **friction_simulator** | Hertz接触、Hamrock-Dowson油膜、λ比判定、4种材料对实验磨损系数、Archard磨损 |
| **pointing_analyzer** | DCM方向余弦矩阵、轴系垂直度/跳动几何误差、4轴误差传递、不确定度合成 |
| **alarm_ws** | 5类告警评估(磨损/力矩/指向×2/接近阈值)、STOMP 4主题推送、告警持久化 |
| **监控** | Actuator健康探针、Prometheus指标(Micrometer)、JVM/HikariCP/Tomcat细粒度指标 |

### 前端能力
| 模块 | 能力 |
|------|------|
| **simplified_armilla_3d.js** | Three.js简仪3D模型、青铜/黄金/玻璃材质系统、轴承装配、三色误差矢量ArrowHelper |
| **bearing_panel.js** | Canvas 2D轴承剖面、Value噪声+FBM过程纹理、5种真实感材质(青铜/铸铁/钢珠/油膜干涉/磨损) |
| **charts.js** | Chart.js趋势图(磨损/力矩/λ比/指向误差) |

### 工程化
- ✅ Docker多阶段构建 (3个Dockerfile)
- ✅ docker-compose一键编排 (7服务)
- ✅ 生产级PostgreSQL配置 (16G内存优化+pg_stat_statements)
- ✅ 12条PostgreSQL索引覆盖核心查询路径
- ✅ Nginx Gzip压缩 (15种MIME类型) + Vite构建时预压缩
- ✅ Vite手动分块 (Three.js/Chart.js/WS独立vendor包)
- ✅ 配置外置 (friction-params.yml / geometric-errors.yml)
- ✅ 6种模拟器工况模式
- ✅ 一键启动脚本 deploy/start.sh

---

## 🚀 快速部署 (5分钟)

### 方式一：一键启动脚本 (推荐Linux/macOS)

```bash
# 1. 克隆项目
git clone <repo-url> && cd AI_solo_coder_task_A_134

# 2. 赋予执行权限
chmod +x deploy/start.sh

# 3. 一键启动 (自动生成.env/密码/构建/启动)
./deploy/start.sh
```

### 方式二：手动 Docker Compose

#### 第1步：环境准备
```bash
cd AI_solo_coder_task_A_134

# 复制并修改环境变量
cp deploy/.env.example .env

# 编辑 .env，设置安全密码 (必须修改!)
vim .env
```

#### 第2步：生成MQTT密码文件
```bash
MQTT_PASS=$(grep MQTT_PASSWORD .env | cut -d= -f2)

# 生成3个用户的密码文件
docker run --rm eclipse-mosquitto:2.0.18 \
  mosquitto_passwd -b -c deploy/mosquitto_passwd backend "$MQTT_PASS"

docker run --rm eclipse-mosquitto:2.0.18 \
  mosquitto_passwd -b deploy/mosquitto_passwd simulator "$MQTT_PASS"

docker run --rm eclipse-mosquitto:2.0.18 \
  mosquitto_passwd -b deploy/mosquitto_passwd frontend "$MQTT_PASS"
```

#### 第3步：构建并启动核心服务
```bash
# 构建所有镜像 (并行加速)
docker compose build --parallel backend frontend simulator

# 启动基础设施 + 应用
docker compose up -d postgres mosquitto

# 等待30s数据库初始化完成后启动应用
sleep 30
docker compose up -d backend frontend

# 查看启动日志
docker compose logs -f backend
```

#### 第4步：启动传感器模拟器 (按需)
```bash
# 启动normal模式模拟器
docker compose --profile simulator up -d simulator

# 或者...切换工况：
# 停止当前模拟器
docker compose stop simulator

# 启动高速巡天模式 (环境变量覆盖)
SIMULATOR_MODE=high_speed SIMULATOR_SPEED_FACTOR=3.0 \
  docker compose --profile simulator up -d simulator
```

### 验证部署

```bash
# 1. 检查所有容器健康状态
docker compose ps

# 2. 验证后端健康
curl http://localhost:8080/actuator/health
# 预期: {"status":"UP","groups":["liveness","readiness"]}

# 3. 验证Prometheus指标
curl http://localhost:8080/actuator/prometheus | head -30

# 4. 浏览器访问前端
# http://localhost
```

---

## ⚙️ 部署配置详解

### docker-compose 服务端口映射

| 服务 | 容器名 | 宿主机端口 | 说明 | 资源限制 |
|------|--------|-----------|------|---------|
| **frontend** | armillary-frontend | 80 | Nginx静态资源+反代 | 0.5核/256M |
| **backend** | armillary-backend | 8080, 9090 | SpringBoot 4模块 | 2核/2G |
| **postgres** | armillary-postgres | 5432 | PostgreSQL 16 | 2核/2G |
| **mosquitto** | armillary-mosquitto | 1883, 9001 | MQTT Broker | 默认 |
| **simulator** | armillary-simulator | - | Python传感器 (可选profiles) | 0.5核/128M |
| **prometheus** | armillary-prometheus | 9091 | 监控采集 (可选profiles) | 默认 |

### profiles使用
```bash
# 仅核心服务 (默认)
docker compose up -d

# 核心 + 模拟器
docker compose --profile simulator up -d

# 核心 + 监控
docker compose --profile monitoring up -d

# 全部
docker compose --profile simulator --profile monitoring up -d
```

---

## 🤖 传感器模拟器用法

### 6种工况模式

| 模式 | 工况描述 | 转速倍率 | 负载倍率 | 磨损倍率 | 典型场景 |
|------|---------|---------|---------|---------|---------|
| `idle` | 怠速空载 | ×0.05 | ×0.1 | ×0.02 | 观测间歇 |
| `normal` | 正常观测 (默认) | ×1.0 | ×1.0 | ×1.0 | 标准研究 |
| `high_load` | 高负载 | ×0.5 | ×2.5 | ×3.0 | 大仪器挂载 |
| `high_speed` | 高转速 | ×8.0 | ×1.0 | ×5.0 | 快速巡天 |
| `continuous` | 长时间连续 | ×1.0 | ×1.0 | ×1.5 | 24h不间断 |
| `fault` | 故障注入 | ×1.0 | ×1.0 | ×10.0 | 告警测试 |

### CLI使用 (本机Python)

```bash
cd simulator

# 安装依赖
pip install -r requirements.txt

# 查看帮助
python sensor_simulator.py --help

# 正常模式
python sensor_simulator.py

# 故障注入模式 (触发告警测试)
python sensor_simulator.py fault

# 高转速模式
python sensor_simulator.py high_speed
```

### 环境变量覆盖参数

| 环境变量 | 说明 | 默认值 |
|---------|------|-------|
| `SIMULATOR_MODE` | 工况模式 (idle/normal等) | normal |
| `SIMULATOR_SPEED_FACTOR` | **额外**转速倍率 (叠加模式系数) | 1.0 |
| `SIMULATOR_LOAD_FACTOR` | **额外**负载倍率 | 1.0 |
| `SIMULATOR_INTERVAL_SECONDS` | 上报间隔秒数 | 60 |
| `INSTRUMENT_ID` | 仪器UUID | a1b2c3d4... |
| `MQTT_BROKER_HOST` | MQTT主机 | localhost |
| `MQTT_BROKER_PORT` | MQTT端口 | 1883 |

### 组合示例

```bash
# 极端工况: 高转速模式 + 额外2倍负载 (相当于 8×速度 × 2×负载)
SIMULATOR_MODE=high_speed SIMULATOR_LOAD_FACTOR=2.0 \
  SIMULATOR_INTERVAL_SECONDS=10 python sensor_simulator.py

# Docker中切换工况
docker compose stop simulator
SIMULATOR_MODE=high_load SIMULATOR_LOAD_FACTOR=2.0 \
  docker compose --profile simulator up -d simulator
```

### 模拟器启动信息示例

```
======================================================================
🏛️  古代天文简仪轴承摩擦传感器模拟器 V2.0
======================================================================
  工况模式:   HIGH_SPEED  [高转速]
  说明:       快速巡天模式（快速扫视全天）
  转速倍率:   ×8.00
  负载倍率:   ×1.00
  磨损倍率:   ×5.0
  上报间隔:   60s (1.0min)
  仪器ID:     a1b2c3d4-e5f6-7890-abcd-ef0123456789
  MQTT:       mosquitto:1883
  主题:       armillary/sensor/data
======================================================================

[14:30:00] #1      磨损累计: 赤道:2.000μm | 赤纬:1.500μm
[14:31:00] #5      磨损累计: 赤道:2.345μm | 赤纬:1.723μm
...
```

---

## 🛠️ 本地开发环境

### 后端开发

```bash
# 启动依赖 (仅数据库和MQTT)
docker compose up -d postgres mosquitto

# 修改 application.yml (非docker profile)
# 确保指向 localhost:5432 和 localhost:1883

# 编译运行 (JDK21+)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 验证
curl http://localhost:8080/actuator/info
```

### 前端开发

```bash
cd frontend

# 安装依赖
npm install

# Vite开发服务器 (自动代理后端)
npm run dev

# 构建生产版本 (Gzip预压缩)
npm run build
```

---

## 📊 Prometheus 核心指标

| 指标 | 含义 |
|------|------|
| `http_server_requests_seconds_*` | HTTP请求延迟分位数 (P50/P95/P99) |
| `jvm_memory_used_bytes` | JVM堆/非堆内存使用 |
| `jvm_gc_pause_seconds` | GC暂停时间 |
| `hikaricp_connections_*` | 数据库连接池使用 |
| `tomcat_sessions_active_current_sessions` | Tomcat活跃会话 |
| `system_cpu_usage` | 系统CPU使用率 |

访问地址:
- Prometheus UI: `http://localhost:9091`
- 指标端点: `http://localhost:8080/actuator/prometheus`

---

## 🗄️ PostgreSQL索引说明

`database/index_optimization.sql` 中创建的核心索引：

| 表 | 索引 | 用途 |
|----|------|------|
| **sensor_data** | (instrument_id, axis_name, timestamp DESC) | 历史趋势查询(复合) |
| **sensor_data** | (timestamp DESC) | 时间范围查询 |
| **sensor_data** | (instrument_id, wear_depth) WHERE wear_depth IS NOT NULL | 磨损阈值告警 |
| **friction_simulation** | (instrument_id, axis_name, simulation_time DESC) | 仿真历史查询 |
| **friction_simulation** | (instrument_id, lambda_ratio) WHERE lambda<1.0 | 边界润滑分析 |
| **alerts** | (instrument_id, is_acknowledged, created_at DESC) | 活跃告警查询 |
| **bearing_config** | (instrument_id, axis_name) UNIQUE | JOIN加速 |

索引监控SQL:
```sql
-- 查看索引使用情况
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
```

---

## 🔧 维护运维指南

### 常用命令

```bash
# 查看所有服务状态
docker compose ps

# 查看后端实时日志 (带错误高亮)
docker compose logs -f --tail=200 backend | grep -E 'ERROR|WARN|磨损|误差'

# 查看模拟器当前工况
docker exec armillary-simulator python -c "import os;print(os.environ.get('SIMULATOR_MODE'))"

# 数据库备份
docker exec armillary-postgres pg_dump -U armillary armillary_simulation \
  | gzip > backup_armillary_$(date +%Y%m%d).sql.gz

# 数据库恢复
gunzip -c backup_armillary_xxx.sql.gz \
  | docker exec -i armillary-postgres psql -U armillary armillary_simulation
```

### 常见问题

| 问题 | 排查 |
|------|------|
| 前端无法连接后端 | 检查 `.env` 端口 → 检查健康状态 `curl /actuator/health` |
| 模拟器无法连接MQTT | 查看 `deploy/mosquitto_passwd` 是否使用`mosquitto_passwd`工具加密 |
| 后端启动慢 | 首次启动需等待PostgreSQL建表(~10s)，查看PostgreSQL日志 |
| 前端Gzip未生效 | 检查Nginx响应头 `Content-Encoding: gzip` |

---

## 📁 目录结构总览

```
AI_solo_coder_task_A_134/
├── backend/                              # SpringBoot 后端
│   ├── pom.xml                          # Actuator/Prometheus依赖已加
│   └── src/main/java/com/astrohistory/armillary/
│       ├── module/                       # 🔴 4个解耦模块
│       │   ├── mqtt_receiver/            # 采集校验
│       │   ├── friction_simulator/       # 摩擦仿真
│       │   ├── pointing_analyzer/        # 精度分析
│       │   └── alarm_ws/                 # 告警推送
│       ├── event/                        # 5个Spring Events事件
│       ├── config/                       # YAML配置+线程池
│       └── simulation/                   # 核心算法模型
├── frontend/                             # 前端
│   ├── vite.config.js                    # Gzip预压缩+手动分块
│   └── src/js/
│       ├── simplified_armilla_3d.js      # 🔴 简仪3D模型(拆分后)
│       └── bearing_panel.js              # 🔴 轴承剖面(拆分后)
├── simulator/                            # 传感器模拟器 (6工况)
├── database/                             # SQL脚本+索引
├── deploy/                               # 配置文件
│   ├── nginx.conf                        # Gzip+SPA路由+WS反代
│   ├── postgresql.conf                   # 16G内存优化
│   ├── mosquitto.conf                    # MQTT生产配置
│   ├── prometheus.yml                    # 监控配置
│   ├── start.sh                          # 一键启动脚本
│   └── .env.example
├── Dockerfile.backend                    # 多阶段构建(Maven→JRE21)
├── Dockerfile.frontend                   # 多阶段构建(Node→Nginx)
├── Dockerfile.simulator                  # Python3.11-slim
├── docker-compose.yml                    # 7服务编排
└── .dockerignore                         # 构建上下文过滤
```

---

## 📜 License

本项目用于**科技史研究用途**，所有算法参数参考《摩擦学原理》(温诗铸)和《滑动轴承设计手册》。

---

**最后更新**: 2026-06-17 | **版本**: V2.0 (工程化版)
