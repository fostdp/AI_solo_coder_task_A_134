package com.astrohistory.armillary.module.mqtt_receiver.validator;

import com.astrohistory.armillary.dto.MqttSensorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SensorDataValidator {

    private static final double MAX_RPM = 1000.0;
    private static final double MAX_TORQUE_NM = 100.0;
    private static final double MAX_WEAR_MM = 10.0;
    private static final double TEMP_MIN_C = -50.0;
    private static final double TEMP_MAX_C = 200.0;
    private static final double MAX_LOAD_KG = 10000.0;
    private static final double MAX_POINTING_ERROR_DEG = 5.0;

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult() {
            this.valid = true;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public ValidationResult validate(MqttSensorMessage message) {
        ValidationResult result = new ValidationResult();

        if (message == null) {
            result.addError("MQTT消息为空");
            return result;
        }

        validateInstrumentId(message, result);
        validateAxisName(message, result);
        validateTimestamp(message, result);
        validateRotationalSpeed(message, result);
        validateFrictionTorque(message, result);
        validateWearDepth(message, result);
        validateTemperature(message, result);
        validateLoads(message, result);
        validatePointingErrors(message, result);

        if (!result.isValid()) {
            log.warn("传感器数据校验失败: instrument={}, axis={}, errors={}",
                    message.getInstrumentId(), message.getAxisName(), result.getErrors());
        }
        if (!result.getWarnings().isEmpty()) {
            log.debug("传感器数据校验警告: instrument={}, axis={}, warnings={}",
                    message.getInstrumentId(), message.getAxisName(), result.getWarnings());
        }

        return result;
    }

    private void validateInstrumentId(MqttSensorMessage message, ValidationResult result) {
        if (message.getInstrumentId() == null || message.getInstrumentId().toString().isEmpty()) {
            result.addError("设备ID为空");
        }
    }

    private void validateAxisName(MqttSensorMessage message, ValidationResult result) {
        if (message.getAxisName() == null || message.getAxisName().trim().isEmpty()) {
            result.addError("轴名称为空");
            return;
        }
        String axis = message.getAxisName().trim();
        if (!axis.equals("赤道轴") && !axis.equals("赤纬轴") &&
            !axis.equals("地平经轴") && !axis.equals("地平纬轴")) {
            result.addWarning("轴名称非标准值: " + axis + " (标准值: 赤道轴/赤纬轴/地平经轴/地平纬轴)");
        }
    }

    private void validateTimestamp(MqttSensorMessage message, ValidationResult result) {
        LocalDateTime timestamp = message.getTimestamp();
        if (timestamp == null) {
            result.addWarning("时间戳为空，将使用服务器当前时间");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (timestamp.isAfter(now.plusMinutes(5))) {
            result.addWarning("时间戳超前超过5分钟: " + timestamp);
        }
        if (timestamp.isBefore(now.minusDays(1))) {
            result.addWarning("时间戳超过1天前: " + timestamp);
        }
    }

    private void validateRotationalSpeed(MqttSensorMessage message, ValidationResult result) {
        BigDecimal speed = message.getRotationalSpeed();
        if (speed == null) {
            result.addWarning("转速为空");
            return;
        }
        double s = speed.doubleValue();
        if (s < 0) {
            result.addError("转速为负值: " + s);
        }
        if (s > MAX_RPM) {
            result.addWarning("转速超过合理范围: " + s + " rpm (上限: " + MAX_RPM + ")");
        }
    }

    private void validateFrictionTorque(MqttSensorMessage message, ValidationResult result) {
        BigDecimal torque = message.getFrictionTorque();
        if (torque == null) {
            result.addWarning("摩擦力矩为空");
            return;
        }
        double t = torque.doubleValue();
        if (t < 0) {
            result.addError("摩擦力矩为负值: " + t);
        }
        if (t > MAX_TORQUE_NM) {
            result.addWarning("摩擦力矩超过合理范围: " + t + " N·m (上限: " + MAX_TORQUE_NM + ")");
        }
    }

    private void validateWearDepth(MqttSensorMessage message, ValidationResult result) {
        BigDecimal wear = message.getWearDepth();
        if (wear == null) {
            result.addWarning("磨损深度为空");
            return;
        }
        double w = wear.doubleValue();
        if (w < 0) {
            result.addError("磨损深度为负值: " + w);
        }
        if (w > MAX_WEAR_MM) {
            result.addWarning("磨损深度超过合理范围: " + w + " mm (上限: " + MAX_WEAR_MM + ")");
        }
    }

    private void validateTemperature(MqttSensorMessage message, ValidationResult result) {
        BigDecimal temp = message.getTemperature();
        if (temp == null) {
            return;
        }
        double t = temp.doubleValue();
        if (t < TEMP_MIN_C || t > TEMP_MAX_C) {
            result.addWarning(String.format("温度超出合理范围: %.2f °C (正常范围: %.0f ~ %.0f)",
                    t, TEMP_MIN_C, TEMP_MAX_C));
        }
    }

    private void validateLoads(MqttSensorMessage message, ValidationResult result) {
        BigDecimal radial = message.getLoadRadial();
        if (radial != null) {
            double r = radial.doubleValue();
            if (r < 0) {
                result.addError("径向载荷为负值: " + r);
            }
            if (r > MAX_LOAD_KG) {
                result.addWarning("径向载荷超过合理范围: " + r + " kg");
            }
        }
        BigDecimal axial = message.getLoadAxial();
        if (axial != null) {
            double a = axial.doubleValue();
            if (a < 0) {
                result.addError("轴向载荷为负值: " + a);
            }
            if (a > MAX_LOAD_KG) {
                result.addWarning("轴向载荷超过合理范围: " + a + " kg");
            }
        }
    }

    private void validatePointingErrors(MqttSensorMessage message, ValidationResult result) {
        BigDecimal az = message.getPointingErrorAz();
        if (az != null && Math.abs(az.doubleValue()) > MAX_POINTING_ERROR_DEG) {
            result.addWarning("方位角指向误差超出合理范围: " + az + " 度");
        }
        BigDecimal alt = message.getPointingErrorAlt();
        if (alt != null && Math.abs(alt.doubleValue()) > MAX_POINTING_ERROR_DEG) {
            result.addWarning("高度角指向误差超出合理范围: " + alt + " 度");
        }
    }
}
