#!/bin/bash
# =============================================
# 快速初始化脚本 - 一键启动完整环境
# =============================================
set -e

echo "🏛️  古代天文简仪轴承摩擦仿真系统 - 一键启动脚本"
echo "=================================================="

# 1. 检查Docker
if ! command -v docker &> /dev/null; then
    echo "❌ 请先安装 Docker Engine 和 Docker Compose"
    exit 1
fi

# 2. 创建.env
if [ ! -f ".env" ]; then
    echo "📝 生成 .env 文件..."
    cp deploy/.env.example .env

    # 生成随机密码
    DB_PASS=$(openssl rand -hex 16 2>/dev/null || echo "armillary_$(date +%s)")
    MQTT_PASS=$(openssl rand -hex 16 2>/dev/null || echo "mqtt_$(date +%s)")

    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/DB_PASSWORD=.*/DB_PASSWORD=$DB_PASS/" .env
        sed -i '' "s/MQTT_PASSWORD=.*/MQTT_PASSWORD=$MQTT_PASS/" .env
    else
        sed -i "s/DB_PASSWORD=.*/DB_PASSWORD=$DB_PASS/" .env
        sed -i "s/MQTT_PASSWORD=.*/MQTT_PASSWORD=$MQTT_PASS/" .env
    fi
    echo "✅ .env 已生成 (随机密码)"
fi

# 3. 创建Mosquitto密码文件 (第一次启动)
if [ ! -f "deploy/mosquitto_passwd_encrypted" ]; then
    echo "🔐 生成 Mosquitto 密码..."
    MQTT_PASS=$(grep MQTT_PASSWORD .env | cut -d= -f2)
    docker run --rm eclipse-mosquitto:2.0.18 \
        mosquitto_passwd -b -c /dev/stdout backend "$MQTT_PASS" > deploy/mosquitto_passwd 2>/dev/null || true
    docker run --rm eclipse-mosquitto:2.0.18 \
        mosquitto_passwd -b /dev/stdin simulator "$MQTT_PASS" 2>/dev/null >> deploy/mosquitto_passwd || true
    touch deploy/mosquitto_passwd_encrypted
fi

# 4. 构建镜像
echo ""
echo "🔨 构建后端和前端镜像..."
echo "这可能需要3-10分钟，请耐心等待..."
docker compose build --parallel backend frontend simulator 2>&1 | tail -20

# 5. 启动基础设施服务
echo ""
echo "🚀 启动基础设施 (PostgreSQL + MQTT)..."
docker compose up -d postgres mosquitto
echo "⏳ 等待 PostgreSQL 和 MQTT 就绪..."
sleep 20

# 6. 启动后端和前端
echo "🚀 启动后端和前端..."
docker compose up -d backend frontend

# 7. 等待后端健康
echo "⏳ 等待 SpringBoot 启动 (~60-90秒)..."
for i in $(seq 1 40); do
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo "✅ 后端服务健康！"
        break
    fi
    sleep 3
    [ $((i % 10)) -eq 0 ] && echo "  ...等待中 (已等待 $((i*3))s)"
done

# 8. 可选：启动模拟器
echo ""
read -p "是否启动传感器模拟器？(y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🚀 启动传感器模拟器 (normal 模式)..."
    docker compose --profile simulator up -d simulator
    echo ""
    echo "💡 提示: 切换工况请使用:"
    echo "   docker compose stop simulator"
    echo "   SIMULATOR_MODE=high_speed docker compose --profile simulator up -d simulator"
fi

echo ""
echo "=================================================="
echo "✅ 系统启动完成！"
echo ""
echo "  🌐 前端界面:       http://localhost"
echo "  ⚙️  后端API:        http://localhost:8080/api"
echo "  📊 健康检查:       http://localhost:8080/actuator/health"
echo "  📈 Prometheus:    http://localhost:9091 (--profile monitoring)"
echo "  🐘 PostgreSQL:    localhost:5432"
echo "  📡 MQTT Broker:    localhost:1883"
echo ""
echo "📖 查看日志:  docker compose logs -f backend"
echo "🛑 停止系统:  docker compose down"
echo "=================================================="
