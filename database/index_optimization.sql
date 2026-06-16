-- =============================================
-- 性能优化：PostgreSQL索引优化脚本
-- 古代天文简仪轴承摩擦仿真系统
-- =============================================
-- 针对6个核心表的查询路径优化
-- =============================================

BEGIN;

-- =============================================
-- 1. sensor_data 表 (最大表，每分钟4条/设备)
-- =============================================
-- 核心查询路径：
--   - 按设备+轴+时间范围查历史
--   - DISTINCT ON 取各轴最新数据
--   - JOIN bearing_config 做仿真

-- 复合索引：设备轴时间排序（最重要的索引）
CREATE INDEX IF NOT EXISTS idx_sensor_data_instr_axis_time
    ON sensor_data (instrument_id, axis_name, timestamp DESC);

-- 时间范围查询索引
CREATE INDEX IF NOT EXISTS idx_sensor_data_timestamp
    ON sensor_data (timestamp DESC);

-- 磨损阈值告警索引
CREATE INDEX IF NOT EXISTS idx_sensor_data_wear
    ON sensor_data (instrument_id, wear_depth)
    WHERE wear_depth IS NOT NULL;

-- 指向误差告警索引
CREATE INDEX IF NOT EXISTS idx_sensor_data_pointing
    ON sensor_data (instrument_id, timestamp)
    WHERE pointing_error_az IS NOT NULL OR pointing_error_alt IS NOT NULL;

-- 自动清理过期数据的分区裁剪
CREATE INDEX IF NOT EXISTS idx_sensor_data_instr_time
    ON sensor_data (instrument_id, timestamp);

ANALYZE sensor_data;

-- =============================================
-- 2. friction_simulation 表 (每分钟4条/设备)
-- =============================================

CREATE INDEX IF NOT EXISTS idx_friction_instr_axis_time
    ON friction_simulation (instrument_id, axis_name, simulation_time DESC);

CREATE INDEX IF NOT EXISTS idx_friction_time
    ON friction_simulation (simulation_time DESC);

-- λ比异常低查询（边界润滑告警）
CREATE INDEX IF NOT EXISTS idx_friction_lambda
    ON friction_simulation (instrument_id, lambda_ratio)
    WHERE lambda_ratio < 1.0;

-- 高磨损率告警
CREATE INDEX IF NOT EXISTS idx_friction_wear_rate
    ON friction_simulation (instrument_id, wear_rate DESC)
    WHERE wear_rate > 0.0001;

ANALYZE friction_simulation;

-- =============================================
-- 3. pointing_analysis 表
-- =============================================

CREATE INDEX IF NOT EXISTS idx_pointing_instr_time
    ON pointing_analysis (instrument_id, analysis_time DESC);

-- 高误差查询
CREATE INDEX IF NOT EXISTS idx_pointing_error
    ON pointing_analysis (instrument_id, total_pointing_error DESC)
    WHERE total_pointing_error > 0.001;

ANALYZE pointing_analysis;

-- =============================================
-- 4. alerts 表 (稀疏表)
-- =============================================

CREATE INDEX IF NOT EXISTS idx_alerts_instr_ack_time
    ON alerts (instrument_id, is_acknowledged, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_level
    ON alerts (alert_level, created_at DESC)
    WHERE alert_level = 'WARNING';

CREATE INDEX IF NOT EXISTS idx_alerts_type
    ON alerts (alert_type, created_at DESC);

ANALYZE alerts;

-- =============================================
-- 5. bearing_config 表 (小表，JOIN频繁)
-- =============================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_bearing_unique
    ON bearing_config (instrument_id, axis_name);

ANALYZE bearing_config;

-- =============================================
-- 6. armillary_instrument 表 (极小表)
-- =============================================

CREATE INDEX IF NOT EXISTS idx_instrument_status
    ON armillary_instrument (is_active, last_online DESC);

ANALYZE armillary_instrument;

-- =============================================
-- 7. 辅助函数统计信息
-- =============================================
ANALYZE;

COMMIT;

-- =============================================
-- 运维建议：
-- 1. 每月运行 VACUUM ANALYZE;
-- 2. 对sensor_data可考虑分区表：按月RANGE分区
-- 3. pg_stat_statements监控TOP 10慢查询
-- 4. 超时索引建议(90天以上)可用DROP CONCURRENTLY
-- =============================================
