-- =============================================
-- 古代天文简仪轴承摩擦仿真与指向精度分析系统
-- PostgreSQL 数据库初始化脚本
-- =============================================

-- 创建数据库
-- CREATE DATABASE armillary_simulation;

-- 连接到数据库
-- \c armillary_simulation;

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================
-- 简仪设备表
-- =============================================
CREATE TABLE IF NOT EXISTS armillary_instruments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    location VARCHAR(200),
    build_year INTEGER,
    description TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 轴承配置表
-- =============================================
CREATE TABLE IF NOT EXISTS bearing_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    instrument_id UUID REFERENCES armillary_instruments(id) ON DELETE CASCADE,
    axis_name VARCHAR(50) NOT NULL,
    axis_type VARCHAR(50) NOT NULL,
    bearing_type VARCHAR(100),
    material VARCHAR(100),
    inner_diameter NUMERIC(10,4),
    outer_diameter NUMERIC(10,4),
    width NUMERIC(10,4),
    initial_clearance NUMERIC(10,6),
    lubricant_viscosity NUMERIC(10,4),
    elastic_modulus NUMERIC(15,2),
    poisson_ratio NUMERIC(5,4),
    hardness NUMERIC(10,2),
    wear_coefficient NUMERIC(10,8),
    max_allowable_wear NUMERIC(10,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 传感器数据表
-- =============================================
CREATE TABLE IF NOT EXISTS sensor_data (
    id BIGSERIAL PRIMARY KEY,
    instrument_id UUID REFERENCES armillary_instruments(id) ON DELETE CASCADE,
    axis_name VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    rotational_speed NUMERIC(12,6),
    friction_torque NUMERIC(15,8),
    wear_depth NUMERIC(12,8),
    pointing_error_az NUMERIC(12,8),
    pointing_error_alt NUMERIC(12,8),
    temperature NUMERIC(8,4),
    load_radial NUMERIC(12,4),
    load_axial NUMERIC(12,4),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sensor_data_instrument_time ON sensor_data(instrument_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sensor_data_timestamp ON sensor_data(timestamp DESC);

-- =============================================
-- 摩擦仿真结果表
-- =============================================
CREATE TABLE IF NOT EXISTS friction_simulation (
    id BIGSERIAL PRIMARY KEY,
    instrument_id UUID REFERENCES armillary_instruments(id) ON DELETE CASCADE,
    axis_name VARCHAR(50) NOT NULL,
    simulation_time TIMESTAMP NOT NULL,
    lambda_ratio NUMERIC(10,6),
    film_thickness NUMERIC(12,8),
    contact_pressure NUMERIC(15,4),
    friction_coefficient NUMERIC(10,8),
    asperity_contact_ratio NUMERIC(10,6),
    wear_rate NUMERIC(15,10),
    total_wear_depth NUMERIC(12,8),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 指向精度分析表
-- =============================================
CREATE TABLE IF NOT EXISTS pointing_analysis (
    id BIGSERIAL PRIMARY KEY,
    instrument_id UUID REFERENCES armillary_instruments(id) ON DELETE CASCADE,
    analysis_time TIMESTAMP NOT NULL,
    target_ra NUMERIC(12,8),
    target_dec NUMERIC(12,8),
    azimuth_error NUMERIC(12,8),
    altitude_error NUMERIC(12,8),
    total_pointing_error NUMERIC(12,8),
    ra_error_source JSONB,
    error_uncertainty NUMERIC(12,8),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 告警表
-- =============================================
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    instrument_id UUID REFERENCES armillary_instruments(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    axis_name VARCHAR(50),
    message TEXT NOT NULL,
    threshold_value NUMERIC(15,8),
    actual_value NUMERIC(15,8),
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerts_instrument_time ON alerts(instrument_id, created_at DESC);

-- =============================================
-- 初始化数据
-- =============================================

-- 插入示例简仪设备
INSERT INTO armillary_instruments (id, name, model, location, build_year, description) VALUES
('a1b2c3d4-e5f6-7890-abcd-ef0123456789', '元代简仪原型机', 'Yuan-Dynasty-Armillary-001', '北京古观象台', 1279, '元代郭守敬创制的简仪复原研究样机，用于天文观测');

-- 插入四游仪赤经环轴承配置
INSERT INTO bearing_config (instrument_id, axis_name, axis_type, bearing_type, material, inner_diameter, outer_diameter, width, initial_clearance, lubricant_viscosity, elastic_modulus, poisson_ratio, hardness, wear_coefficient, max_allowable_wear) VALUES
('a1b2c3d4-e5f6-7890-abcd-ef0123456789', '赤道轴', 'EQUATORIAL', '滑动轴承', '青铜', 120.0, 150.0, 80.0, 0.005, 0.025, 110000.0, 0.34, 180.0, 2.0e-8, 0.1),
('a1b2c3d4-e5f6-7890-abcd-ef0123456789', '赤纬轴', 'DECLINATION', '滑动轴承', '青铜', 100.0, 130.0, 70.0, 0.004, 0.025, 110000.0, 0.34, 180.0, 2.0e-8, 0.08),
('a1b2c3d4-e5f6-7890-abcd-ef0123456789', '地平经轴', 'AZIMUTH', '滚动轴承', '钢', 80.0, 120.0, 60.0, 0.003, 0.02, 206000.0, 0.3, 60.0, 1.5e-8, 0.06),
('a1b2c3d4-e5f6-7890-abcd-ef0123456789', '地平纬轴', 'ALTITUDE', '滚动轴承', '钢', 70.0, 110.0, 55.0, 0.003, 0.02, 206000.0, 0.3, 60.0, 1.5e-8, 0.06);

-- =============================================
-- 创建视图
-- =============================================

-- 最新传感器数据视图
CREATE OR REPLACE VIEW latest_sensor_data AS
SELECT DISTINCT ON (instrument_id, axis_name)
    id, instrument_id, axis_name, timestamp,
    rotational_speed, friction_torque, wear_depth,
    pointing_error_az, pointing_error_alt, temperature,
    load_radial, load_axial, calculated_at
FROM sensor_data
ORDER BY instrument_id, axis_name, timestamp DESC;

-- 当前告警视图
CREATE OR REPLACE VIEW active_alerts AS
SELECT * FROM alerts
WHERE is_acknowledged = FALSE
ORDER BY created_at DESC;

-- =============================================
-- 创建函数
-- =============================================

-- 计算两点间的函数
CREATE OR REPLACE FUNCTION calculate_total_pointing_error(az_err NUMERIC, alt_err NUMERIC)
RETURNS NUMERIC AS $$
BEGIN
    RETURN SQRT(az_err * az_err + alt_err * alt_err);
END;
$$ LANGUAGE plpgsql;

-- 磨损率计算函数
CREATE OR REPLACE FUNCTION calculate_wear_rate(
    friction_coeff NUMERIC,
    load NUMERIC,
    sliding_distance NUMERIC,
    hardness NUMERIC,
    wear_coeff NUMERIC
) RETURNS NUMERIC AS $$
BEGIN
    RETURN wear_coeff * friction_coeff * load * sliding_distance / hardness;
END;
$$ LANGUAGE plpgsql;
