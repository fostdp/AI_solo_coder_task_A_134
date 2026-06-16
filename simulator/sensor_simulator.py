#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代天文简仪 MQTT 传感器模拟器 V2.0
===================================
支持6种工况模式，可动态调节转速/负载/温度/磨损系数

工况列表:
    idle        - 怠速空载 (观测间休息)
    normal      - 正常观测 (默认)
    high_load   - 高负载 (大质量仪器挂载)
    high_speed  - 高转速 (快速巡天模式)
    continuous  - 连续长时间运行 (24h不间断)
    fault       - 故障注入模式 (磨损异常)
"""

import json
import time
import math
import random
import threading
import signal
import sys
import os
import uuid as _uuid
from datetime import datetime, timezone
from pathlib import Path
from dataclasses import dataclass, field
from typing import Dict, Any

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("❌ 请先安装依赖: pip install paho-mqtt")
    sys.exit(1)

# =============================================
# 6种预设工况定义
# =============================================
OPERATING_MODES: Dict[str, Dict[str, Any]] = {
    "idle": {
        "label": "怠速空载",
        "description": "观测间休息状态，转速极低，空载",
        "speed_factor": 0.05,        # 转速×0.05
        "load_factor": 0.1,          # 载荷×0.1
        "torque_factor": 0.2,        # 力矩×0.2
        "wear_factor": 0.02,         # 磨损×0.02
        "temp_base_offset": 2.0,     # 接近室温
        "temp_rise_per_hour": 0.05,  # 温升可忽略
    },
    "normal": {
        "label": "正常观测",
        "description": "标准天文观测条件 (默认)",
        "speed_factor": 1.0,
        "load_factor": 1.0,
        "torque_factor": 1.0,
        "wear_factor": 1.0,
        "temp_base_offset": 5.0,
        "temp_rise_per_hour": 0.5,
    },
    "high_load": {
        "label": "高负载",
        "description": "挂载大质量光学仪器（如折反射望远镜）",
        "speed_factor": 0.5,
        "load_factor": 2.5,          # 2.5倍负载
        "torque_factor": 1.8,
        "wear_factor": 3.0,           # 磨损显著增加
        "temp_base_offset": 10.0,
        "temp_rise_per_hour": 1.2,
    },
    "high_speed": {
        "label": "高转速",
        "description": "快速巡天模式（快速扫视全天）",
        "speed_factor": 8.0,          # 8倍转速
        "load_factor": 1.0,
        "torque_factor": 2.0,
        "wear_factor": 5.0,           # 高转速高磨损
        "temp_base_offset": 8.0,
        "temp_rise_per_hour": 2.5,    # 快速温升
    },
    "continuous": {
        "label": "连续长时间运行",
        "description": "24小时不间断观测（长期精度退化测试）",
        "speed_factor": 1.0,
        "load_factor": 1.0,
        "torque_factor": 1.2,
        "wear_factor": 1.5,
        "temp_base_offset": 15.0,     # 基础温度偏高
        "temp_rise_per_hour": 0.8,    # 持续升温
    },
    "fault": {
        "label": "故障注入",
        "description": "故意引入异常磨损（边界润滑模拟）",
        "speed_factor": 1.0,
        "load_factor": 1.0,
        "torque_factor": 2.5,
        "wear_factor": 10.0,          # 10倍磨损！
        "temp_base_offset": 25.0,
        "temp_rise_per_hour": 3.0,
    },
}

# =============================================
# 数据类
# =============================================
@dataclass
class AxisState:
    name: str
    base_speed: float
    base_torque: float
    wear_rate: float
    accumulated_wear: float
    base_radial: float
    base_axial: float
    current_temp: float = 25.0
    fault_axis: bool = False
    fault_severity: float = 1.0

@dataclass
class RuntimeState:
    mode: str = "normal"
    start_time: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    elapsed_seconds: float = 0.0
    message_count: int = 0
    running: bool = False

class ArmillarySensorSimulator:
    """
    简仪传感器模拟器

    使用:
        simulator = ArmillarySensorSimulator(config_path, mode="high_speed")
        simulator.start()

    或通过环境变量:
        export SIMULATOR_MODE=high_speed
        export SIMULATOR_SPEED_FACTOR=3.5
        export SIMULATOR_LOAD_FACTOR=2.0
    """

    def __init__(self, config_path, mode=None, override_speed=None, override_load=None,
                 override_interval=None, override_instrument_id=None):
        self.runtime = RuntimeState()
        self.axis_states: Dict[str, AxisState] = {}
        self.mqtt_client = None

        self._load_config(config_path)

        # 工况模式
        env_mode = os.environ.get("SIMULATOR_MODE")
        self.mode = (mode or env_mode or
                     self.simulation_config.get("operatingMode", "normal"))
        if self.mode not in OPERATING_MODES:
            print(f"⚠️  未知工况 '{self.mode}'，使用 normal")
            self.mode = "normal"
        self.mode_params = OPERATING_MODES[self.mode]

        # 覆盖参数（命令行 / 环境变量优先级最高）
        env_speed = os.environ.get("SIMULATOR_SPEED_FACTOR")
        env_load = os.environ.get("SIMULATOR_LOAD_FACTOR")
        env_interval = os.environ.get("SIMULATOR_INTERVAL_SECONDS")
        env_instrument = os.environ.get("INSTRUMENT_ID")
        env_name = os.environ.get("INSTRUMENT_NAME")

        self.override_speed = float(override_speed or env_speed or 1.0)
        self.override_load = float(override_load or env_load or 1.0)

        override_interval = int(override_interval or env_interval or 0)
        if override_interval > 0:
            self.report_interval = override_interval

        if env_instrument or override_instrument_id:
            self.instrument_id = (override_instrument_id or env_instrument)
        try:
            if self.instrument_id and len(self.instrument_id) < 32:
                # UUID格式校验
                _uuid.UUID(self.instrument_id)
        except (ValueError, AttributeError):
            pass

        if env_name:
            self.instrument_config["name"] = env_name

        # 打印工况信息
        self._print_mode_banner()

        # 初始化各轴状态
        for axis in self.instrument_config["axes"]:
            fi_cfg = self.simulation_config.get("faultInjection", {})
            is_fault = (self.mode == "fault" or
                        (fi_cfg.get("enabled") and
                         fi_cfg.get("faultAxis") == axis["name"]))
            severity = fi_cfg.get("faultSeverity", 2.0) if is_fault and self.mode != "fault" else 1.0

            self.axis_states[axis["name"]] = AxisState(
                name=axis["name"],
                base_speed=axis["baseRotationSpeed"],
                base_torque=axis["baseFrictionTorque"],
                wear_rate=axis["wearRateFactor"],
                accumulated_wear=axis.get("initialWearDepth", 0.0),
                base_radial=axis.get("radialLoad", 500),
                base_axial=axis.get("axialLoad", 100),
                fault_axis=is_fault,
                fault_severity=severity
            )

        # 信号处理
        signal.signal(signal.SIGINT, self._shutdown)
        signal.signal(signal.SIGTERM, self._shutdown)

    def _load_config(self, config_path):
        config_file = Path(config_path)
        if not config_file.exists():
            raise FileNotFoundError(f"配置文件不存在: {config_path}")

        with open(config_file, 'r', encoding='utf-8') as f:
            config = json.load(f)

        self.mqtt_config = config["mqtt"]
        self.instrument_config = config["instrument"]
        self.simulation_config = config.get("simulation", {})
        self.instrument_id = self.instrument_config["id"]
        self.report_interval = self.instrument_config.get("reportInterval", 60000) / 1000

        # MQTT环境变量覆盖
        self.mqtt_config["broker"] = os.environ.get(
            "MQTT_BROKER_HOST", self.mqtt_config["broker"])
        self.mqtt_config["port"] = int(os.environ.get(
            "MQTT_BROKER_PORT", str(self.mqtt_config["port"])))
        self.mqtt_config["username"] = os.environ.get(
            "MQTT_USERNAME", self.mqtt_config.get("username", ""))
        self.mqtt_config["password"] = os.environ.get(
            "MQTT_PASSWORD", self.mqtt_config.get("password", ""))
        self.topic = os.environ.get(
            "MQTT_TOPIC", f"{self.mqtt_config.get('topicPrefix','armillary')}/sensor/data")

    def _print_mode_banner(self):
        m = self.mode_params
        print("\n" + "=" * 70)
        print("🏛️  古代天文简仪轴承摩擦传感器模拟器 V2.0")
        print("=" * 70)
        print(f"  工况模式:   {self.mode.upper()}  [{m['label']}]")
        print(f"  说明:       {m['description']}")
        print(f"  转速倍率:   ×{m['speed_factor'] * self.override_speed:.2f}")
        print(f"  负载倍率:   ×{m['load_factor'] * self.override_load:.2f}")
        print(f"  磨损倍率:   ×{m['wear_factor']:.1f}")
        print(f"  上报间隔:   {self.report_interval}s ({self.report_interval / 60:.1f}min)")
        print(f"  仪器ID:     {self.instrument_id}")
        print(f"  MQTT:       {self.mqtt_config['broker']}:{self.mqtt_config['port']}")
        print(f"  主题:       {self.topic}")
        print("=" * 70 + "\n")

    # ========== MQTT连接 ==========
    def connect_mqtt(self):
        client_id = f"{self.mqtt_config.get('clientId', 'armillary-sim')}-{int(time.time())}"
        self.mqtt_client = mqtt.Client(
            client_id=client_id, callback_api_version=mqtt.CallbackAPIVersion.VERSION2)

        if self.mqtt_config.get("username"):
            self.mqtt_client.username_pw_set(
                self.mqtt_config["username"],
                self.mqtt_config.get("password", ""))

        self.mqtt_client.on_connect = self._on_connect
        self.mqtt_client.on_disconnect = self._on_disconnect
        self.mqtt_client.on_publish = self._on_publish

        print(f"🔌 连接MQTT: {self.mqtt_config['broker']}:{self.mqtt_config['port']} ...")
        for attempt in range(1, 21):
            try:
                self.mqtt_client.connect(
                    self.mqtt_config["broker"],
                    self.mqtt_config["port"], keepalive=60)
                self.mqtt_client.loop_start()
                return
            except Exception as e:
                wait = min(attempt * 2, 30)
                print(f"  ⏳ 连接尝试 {attempt} 失败: {e}，{wait}s后重试...")
                time.sleep(wait)
        print("❌ MQTT连接超时，请检查Broker状态")
        sys.exit(1)

    def _on_connect(self, client, userdata, flags, rc, properties=None):
        if rc == 0:
            print("✅ MQTT连接成功，开始上报传感器数据...\n")
        else:
            print(f"❌ MQTT连接失败 rc={rc}")

    def _on_disconnect(self, client, userdata, rc, properties=None):
        if self.runtime.running:
            print(f"⚠️  MQTT断开(rc={rc})，后台自动重连...")

    def _on_publish(self, client, userdata, mid, reason_code=None, properties=None):
        pass

    # ========== 核心数据生成 ==========
    def _generate_axis_data(self, axis: AxisState) -> Dict[str, Any]:
        m = self.mode_params
        noise = self.simulation_config.get("noiseLevel", 0.05)
        wear_accel = self.simulation_config.get("wearAccelerationFactor", 1.0)

        hours_run = self.runtime.elapsed_seconds / 3600.0

        # 1. 转速 (基础×工况×用户覆盖×噪声 + 微小波动)
        base_speed = axis.base_speed * m["speed_factor"] * self.override_speed
        oscillation = 0.02 * math.sin(self.runtime.elapsed_seconds * 0.1)
        speed_noise = random.uniform(-noise, noise) * max(base_speed, 0.01)
        final_speed = max(0.0, base_speed * (1 + speed_noise) + oscillation)

        # 2. 载荷 (基础×工况×用户覆盖×波动，模拟观测目标切换)
        load_noise = random.uniform(-0.08, 0.08)
        target_shift = 0.03 * math.sin(self.runtime.elapsed_seconds / 47.0)
        radial = max(0.0, axis.base_radial * m["load_factor"] *
                     self.override_load * (1 + load_noise + target_shift))
        axial = max(0.0, axis.base_axial * m["load_factor"] *
                    self.override_load * (1 + load_noise * 0.5))

        # 3. 摩擦力矩 (与载荷×转速0.5次幂正相关)
        torque_base = axis.base_torque * m["torque_factor"]
        load_contrib = (radial / max(axis.base_radial, 1)) ** 0.6
        speed_contrib = (final_speed / max(axis.base_speed, 0.01) /
                         max(m["speed_factor"], 0.1)) ** 0.5
        wear_penalty = 1 + (axis.accumulated_wear / max(axis.wear_rate * 3600 * 24 * 365, 1e-6)) ** 0.3
        final_torque = (torque_base * load_contrib *
                        (0.5 + 0.5 * speed_contrib) * wear_penalty)
        final_torque = final_torque * (1 + random.uniform(-noise, noise))

        # 4. 温度 (基础+温升，叠加环境波动)
        ambient_temp = 25.0 + 3.0 * math.sin(hours_run * 2 * math.pi / 24)
        temp_rise = (m["temp_base_offset"] +
                     m["temp_rise_per_hour"] * min(hours_run, 72) *
                     (1 - math.exp(-hours_run / 4.0)))
        axis.current_temp = ambient_temp + temp_rise + random.gauss(0, 0.3)

        # 5. 磨损累积 (Archard公式: K·μ·W·v)
        # 简化: 当前值 × dt × 工况系数 × 磨损加速
        dt = self.report_interval
        inst_rate = (axis.wear_rate *
                     final_torque * final_speed *
                     m["wear_factor"] * wear_accel /
                     max(axis.base_torque * axis.base_speed, 1e-6))
        if axis.fault_axis:
            inst_rate *= axis.fault_severity
        axis.accumulated_wear += inst_rate * dt / 3600.0

        # 6. 指向误差 (与磨损正相关，叠加随机误差)
        wear_error = (axis.accumulated_wear / 0.1) * 0.5
        azimuth_err = wear_error * random.uniform(0.7, 1.3) + random.gauss(0, 0.01)
        altitude_err = wear_error * random.uniform(0.7, 1.3) * 0.8 + random.gauss(0, 0.008)

        return {
            "instrumentId": str(self.instrument_id),
            "axisName": axis.name,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "rotationalSpeed": round(final_speed, 6),
            "frictionTorque": round(final_torque, 6),
            "wearDepth": round(axis.accumulated_wear, 8),
            "pointingErrorAz": round(azimuth_err, 6),
            "pointingErrorAlt": round(altitude_err, 6),
            "temperature": round(axis.current_temp, 2),
            "loadRadial": round(radial, 1),
            "loadAxial": round(axial, 1),
        }

    # ========== 单轮数据上报 ==========
    def _tick(self):
        self.runtime.elapsed_seconds += self.report_interval
        tick = self.runtime.message_count

        # 每轴依次上报
        for i, (axis_name, axis_state) in enumerate(self.axis_states.items()):
            data = self._generate_axis_data(axis_state)
            payload = json.dumps(data, ensure_ascii=False)

            try:
                self.mqtt_client.publish(self.topic, payload=payload, qos=1)
                self.runtime.message_count += 1

                if tick % 10 == 0 and i == 0:
                    wear_info = " | ".join(
                        f"{n[:3]}:{s.accumulated_wear*1000:.3f}μm"
                        for n, s in list(self.axis_states.items())[:2]
                    )
                    print(f"[{datetime.now():%H:%M:%S}] "
                          f"#{self.runtime.message_count:<6d} "
                          f"磨损累计: {wear_info}")
            except Exception as e:
                print(f"  ❌ 上报失败: {e}")

    # ========== 启动循环 ==========
    def start(self):
        self.runtime.running = True
        self.connect_mqtt()
        time.sleep(1.5)

        try:
            while self.runtime.running:
                tick_start = time.time()
                self._tick()
                elapsed = time.time() - tick_start
                sleep_for = max(0.05, self.report_interval - elapsed)
                time.sleep(sleep_for)
        except KeyboardInterrupt:
            self._shutdown(signal.SIGINT, None)

    def _shutdown(self, signum, frame):
        if not self.runtime.running:
            return
        self.runtime.running = False
        run_time = (datetime.now(timezone.utc) -
                    self.runtime.start_time).total_seconds()
        print(f"\n{'=' * 70}")
        print("📊 模拟结束统计:")
        print(f"  运行时长: {run_time/3600:.2f}h ({int(run_time)}s)")
        print(f"  工况模式: {self.mode}")
        print(f"  总上报数: {self.runtime.message_count:,} 条")
        for name, state in self.axis_states.items():
            print(f"  {name:>8s}: 累计磨损 = {state.accumulated_wear*1000:.4f} μm")
        print("=" * 70)

        try:
            if self.mqtt_client:
                self.mqtt_client.loop_stop()
                self.mqtt_client.disconnect()
        except Exception:
            pass

        sys.exit(0)

# =============================================
# CLI 入口
# =============================================
def print_usage():
    print("""
用法:
  python sensor_simulator.py [模式] [选项]

工况模式:
  idle         怠速空载
  normal       正常观测 (默认)
  high_load    高负载 (2.5×载荷)
  high_speed   高转速 (8×转速)
  continuous   长时间连续运行
  fault        故障注入 (10×磨损)

环境变量:
  SIMULATOR_MODE=high_speed
  SIMULATOR_SPEED_FACTOR=3.5        额外转速倍率
  SIMULATOR_LOAD_FACTOR=2.0         额外负载倍率
  SIMULATOR_INTERVAL_SECONDS=10     自定义上报间隔(秒)
  INSTRUMENT_ID=<uuid>              覆盖设备ID
  MQTT_BROKER_HOST=mosquitto
  MQTT_BROKER_PORT=1883

示例:
  # 正常模式
  python sensor_simulator.py

  # 高转速+2倍额外负载
  python sensor_simulator.py high_speed
  SIMULATOR_LOAD_FACTOR=2.0 python sensor_simulator.py high_speed

  # 故障注入测试
  python sensor_simulator.py fault
""")

if __name__ == "__main__":
    args = sys.argv[1:]

    if "-h" in args or "--help" in args:
        print_usage()
        sys.exit(0)

    mode_arg = args[0] if args and args[0] in OPERATING_MODES else None
    config_path = Path(__file__).parent / "config.json"

    try:
        simulator = ArmillarySensorSimulator(
            str(config_path),
            mode=mode_arg
        )
        simulator.start()
    except KeyboardInterrupt:
        pass
    except Exception as e:
        print(f"❌ 模拟器启动失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
