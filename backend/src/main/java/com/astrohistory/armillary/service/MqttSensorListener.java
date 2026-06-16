package com.astrohistory.armillary.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @deprecated 已被 MqttReceiverModule 替代。
 * 旧版本MQTT监听器，保留用于向后兼容，请勿在新代码中使用。
 * 新实现位于: com.astrohistory.armillary.module.mqtt_receiver.MqttReceiverModule
 */
@Deprecated(since = "2.0", forRemoval = true)
@Slf4j
@Component
public class MqttSensorListener {

    public MqttSensorListener() {
        log.warn("⚠️ [DEPRECATED] MqttSensorListener已废弃，请使用MqttReceiverModule。" +
                "此类将在v3.0移除。");
    }
}
