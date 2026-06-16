import json
import time
import math
import random
import threading
import signal
import sys
from datetime import datetime, timezone
from pathlib import Path

import paho.mqtt.client as mqtt


class ArmillarySensorSimulator:
    def __init__(self, config_path):
        self.load_config(config_path)
        self.running = False
        self.mqtt_client = None
        self.accumulated_wear = {}
        self.start_time = datetime.now(timezone.utc)
        self.message_count = 0

        for axis in self.instrument_config['axes']:
            self.accumulated_wear[axis['name']] = axis.get('initialWearDepth', 0.0)

        signal.signal(signal.SIGINT, self.handle_shutdown)
        signal.signal(signal.SIGTERM, self.handle_shutdown)

    def load_config(self, config_path):
        config_file = Path(config_path)
        if not config_file.exists():
            raise FileNotFoundError(f"配置文件不存在: {config_path}")

        with open(config_file, 'r', encoding='utf-8') as f:
            config = json.load(f)

        self.mqtt_config = config['mqtt']
        self.instrument_config = config['instrument']
        self.simulation_config = config['simulation']
        self.instrument_id = self.instrument_config['id']
        self.report_interval = self.instrument_config.get('reportInterval', 60000) / 1000

    def connect_mqtt(self):
        client_id = f"{self.mqtt_config['clientId']}-{int(time.time())}"
        self.mqtt_client = mqtt.Client(client_id=client_id, callback_api_version=mqtt.CallbackAPIVersion.VERSION2)

        if self.mqtt_config.get('username'):
            self.mqtt_client.username_pw_set(
                self.mqtt_config['username'],
                self.mqtt_config.get('password', '')
            )

        self.mqtt_client.on_connect = self.on_connect
        self.mqtt_client.on_disconnect = self.on_disconnect
        self.mqtt_client.on_publish = self.on_publish

        print(f"正在连接MQTT Broker: {self.mqtt_config['broker']}:{self.mqtt_config['port']}")
        self.mqtt_client.connect(
            self.mqtt_config['broker'],
            self.mqtt_config['port'],
            keepalive=60
        )
        self.mqtt_client.loop_start()

    def on_connect(self, client, userdata, flags, rc, properties=None):
        if rc == 0:
            print("✅ MQTT连接成功")
        else:
            print(f"❌ MQTT连接失败，错误码: {rc}")

    def on_disconnect(self, client, userdata, rc, properties=None):
        print(f"⚠️  MQTT断开连接，错误码: {rc}")
        if self.running:
            print("尝试重新连接...")
            self.schedule_reconnect()

    def on_publish(self, client, userdata, mid, reason_code=None, properties=None):
        pass

    def schedule_reconnect(self):
        def reconnect():
            time.sleep(5)
            try:
                self.mqtt_client.reconnect()
            except Exception as e:
                print(f"重连失败: {e}")
                self.schedule_reconnect()

        thread = threading.Thread(target=reconnect, daemon=True)
        thread.start()

    def generate_sensor_data(self, axis_config, elapsed_seconds):
        noise_level = self.simulation_config.get('noiseLevel', 0.05)
        wear_acceleration = self.simulation_config.get('wearAccelerationFactor', 1.0)

        base_speed = axis_config['baseRotationSpeed']
        base_torque = axis_config['baseFrictionTorque']
        wear_rate = axis_config['wearRateFactor']

        speed_noise = random.uniform(-noise_level, noise_level) * base_speed
        rotation_speed = base_speed + speed_noise

        torque_noise = random.uniform(-noise_level, noise_level) * base_torque
        wear_factor = 1.0 + self.accumulated_wear[axis_config['name']] * 10
        friction_torque = base_torque * wear_factor + torque_noise

        wear_increment = wear_rate * abs(rotation_speed) * self.report_interval * wear_acceleration

        fault_config = self.simulation_config.get('faultInjection', {})
        if fault_config.get('enabled', False):
            if fault_config.get('faultAxis') == axis_config['name']:
                severity = fault_config.get('faultSeverity', 2.0)
                wear_increment *= severity
                friction_torque *= severity

        self.accumulated_wear[axis_config['name']] += wear_increment
        wear_depth = self.accumulated_wear[axis_config['name']]

        wear_noise = random.uniform(-0.01, 0.01) * wear_depth
        wear_depth = wear_depth + wear_noise

        pointing_error = self.calculate_pointing_error(wear_depth, friction_torque, base_torque)
        pointing_noise = random.uniform(-noise_level * 0.1, noise_level * 0.1)
        pointing_error += pointing_noise

        return {
            'axisName': axis_config['name'],
            'rotationSpeed': round(rotation_speed, 6),
            'frictionTorque': round(friction_torque, 6),
            'wearDepth': round(wear_depth, 8),
            'pointingError': round(pointing_error, 8),
            'radialLoad': axis_config.get('radialLoad', 1000),
            'axialLoad': axis_config.get('axialLoad', 200),
            'temperature': round(25.0 + random.uniform(-2, 5), 2),
            'elapsedHours': round(elapsed_seconds / 3600, 4)
        }

    def calculate_pointing_error(self, wear_depth, friction_torque, base_torque):
        deg_to_rad = math.pi / 180
        arcmin_to_deg = 1.0 / 60

        wear_error = wear_depth * 500
        torque_error = (friction_torque - base_torque) * 0.5
        elastic_error = 0.02

        total_error = math.sqrt(wear_error**2 + torque_error**2 + elastic_error**2)
        total_error_deg = total_error * arcmin_to_deg

        return total_error_deg

    def build_mqtt_message(self, axis_data_list):
        return {
            'instrumentId': self.instrument_id,
            'instrumentName': self.instrument_config['name'],
            'timestamp': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z',
            'messageId': f"msg-{self.message_count:06d}",
            'sensorData': axis_data_list
        }

    def publish_data(self):
        elapsed = (datetime.now(timezone.utc) - self.start_time).total_seconds()

        axis_data_list = []
        for axis_config in self.instrument_config['axes']:
            sensor_data = self.generate_sensor_data(axis_config, elapsed)
            axis_data_list.append(sensor_data)

        mqtt_message = self.build_mqtt_message(axis_data_list)
        topic = f"{self.mqtt_config['topicPrefix']}/{self.instrument_id}"

        payload = json.dumps(mqtt_message, ensure_ascii=False)

        try:
            result = self.mqtt_client.publish(topic, payload, qos=1)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                self.message_count += 1
                print(f"\n[{datetime.now().strftime('%H:%M:%S')}] 上报 #{self.message_count}")
                print(f"  主题: {topic}")
                print(f"  数据点: {len(axis_data_list)} 个轴")
                for data in axis_data_list:
                    wear_percent = min(data['wearDepth'] / 0.1 * 100, 100)
                    status = "🔴" if wear_percent > 80 else "🟡" if wear_percent > 50 else "🟢"
                    print(f"  {status} {data['axisName']}: "
                          f"转速={data['rotationSpeed']:.4f} rad/s, "
                          f"力矩={data['frictionTorque']:.4f} N·m, "
                          f"磨损={data['wearDepth']:.6f} mm ({wear_percent:.1f}%), "
                          f"指向误差={data['pointingError']*60:.4f} 角分")
            else:
                print(f"❌ 发布失败，错误码: {result.rc}")
        except Exception as e:
            print(f"❌ 发布异常: {e}")

    def start(self):
        print("\n" + "=" * 60)
        print("古代天文简仪传感器模拟器")
        print("=" * 60)
        print(f"设备ID: {self.instrument_id}")
        print(f"设备名称: {self.instrument_config['name']}")
        print(f"上报间隔: {self.report_interval} 秒")
        print(f"轴数量: {len(self.instrument_config['axes'])}")
        print(f"MQTT Broker: {self.mqtt_config['broker']}:{self.mqtt_config['port']}")
        print(f"主题前缀: {self.mqtt_config['topicPrefix']}")
        print("=" * 60)

        self.connect_mqtt()
        time.sleep(2)

        self.running = True

        print("\n开始模拟数据采集... (Ctrl+C 停止)")

        def publish_loop():
            while self.running:
                self.publish_data()
                time.sleep(self.report_interval)

        try:
            publish_loop()
        except KeyboardInterrupt:
            self.handle_shutdown(None, None)

    def handle_shutdown(self, signum, frame):
        if self.running:
            print("\n\n收到停止信号，正在关闭...")
            self.running = False

            if self.mqtt_client:
                self.mqtt_client.loop_stop()
                self.mqtt_client.disconnect()
                print("MQTT连接已关闭")

            total_hours = (datetime.now(timezone.utc) - self.start_time).total_seconds() / 3600
            print(f"\n运行统计:")
            print(f"  运行时长: {total_hours:.4f} 小时")
            print(f"  上报次数: {self.message_count}")
            print(f"\n最终磨损状态:")
            for axis_name, wear in self.accumulated_wear.items():
                wear_percent = min(wear / 0.1 * 100, 100)
                print(f"  {axis_name}: {wear:.8f} mm ({wear_percent:.2f}%)")

            print("\n模拟器已停止")
            sys.exit(0)


def main():
    config_path = Path(__file__).parent / 'config.json'

    try:
        simulator = ArmillarySensorSimulator(str(config_path))
        simulator.start()
    except FileNotFoundError as e:
        print(f"❌ {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ 启动失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
