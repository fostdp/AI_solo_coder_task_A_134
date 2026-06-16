-- =============================================
-- 数据库升级脚本 V1.0 -> V2.0
-- 古代天文简仪轴承摩擦仿真与指向精度分析系统
-- =============================================
-- 变更内容：
-- 1. bearing_config表新增材料和几何误差字段
-- 2. pointing_analysis表新增几何误差字段
-- =============================================

BEGIN;

-- =============================================
-- bearing_config表新增字段
-- =============================================

ALTER TABLE bearing_config
ADD COLUMN IF NOT EXISTS inner_ring_material VARCHAR(100),
ADD COLUMN IF NOT EXISTS outer_ring_material VARCHAR(100),
ADD COLUMN IF NOT EXISTS rolling_element_material VARCHAR(100),
ADD COLUMN IF NOT EXISTS surface_roughness_ra NUMERIC(8,4),
ADD COLUMN IF NOT EXISTS perpendicularity_error NUMERIC(10,8),
ADD COLUMN IF NOT EXISTS axial_runout NUMERIC(10,8),
ADD COLUMN IF NOT EXISTS radial_runout NUMERIC(10,8);

-- 更新现有数据的材料字段
UPDATE bearing_config
SET
    inner_ring_material = CASE
        WHEN axis_name = '赤道轴' THEN '锡青铜'
        WHEN axis_name = '赤纬轴' THEN '锡青铜'
        ELSE '轴承钢'
    END,
    outer_ring_material = '灰铸铁',
    rolling_element_material = '轴承钢',
    surface_roughness_ra = 0.4,
    perpendicularity_error = 0.01,
    axial_runout = 0.005,
    radial_runout = 0.003
WHERE inner_ring_material IS NULL;

-- 添加注释
COMMENT ON COLUMN bearing_config.inner_ring_material IS '内圈材料';
COMMENT ON COLUMN bearing_config.outer_ring_material IS '外圈材料';
COMMENT ON COLUMN bearing_config.rolling_element_material IS '滚动体材料';
COMMENT ON COLUMN bearing_config.surface_roughness_ra IS '表面粗糙度Ra (μm)';
COMMENT ON COLUMN bearing_config.perpendicularity_error IS '垂直度误差 (度)';
COMMENT ON COLUMN bearing_config.axial_runout IS '轴向跳动误差 (度)';
COMMENT ON COLUMN bearing_config.radial_runout IS '径向跳动误差 (度)';

-- =============================================
-- pointing_analysis表新增字段
-- =============================================

ALTER TABLE pointing_analysis
ADD COLUMN IF NOT EXISTS perpendicularity_error_equatorial NUMERIC(12,8),
ADD COLUMN IF NOT EXISTS perpendicularity_error_altaz NUMERIC(12,8),
ADD COLUMN IF NOT EXISTS axial_runout_error NUMERIC(12,8),
ADD COLUMN IF NOT EXISTS radial_runout_error NUMERIC(12,8),
ADD COLUMN IF NOT EXISTS geometric_error_contribution NUMERIC(12,8);

-- 添加注释
COMMENT ON COLUMN pointing_analysis.perpendicularity_error_equatorial IS '赤道-赤纬轴系垂直度误差 (度)';
COMMENT ON COLUMN pointing_analysis.perpendicularity_error_altaz IS '地平经-纬轴系垂直度误差 (度)';
COMMENT ON COLUMN pointing_analysis.axial_runout_error IS '轴向跳动误差贡献 (度)';
COMMENT ON COLUMN pointing_analysis.radial_runout_error IS '径向跳动误差贡献 (度)';
COMMENT ON COLUMN pointing_analysis.geometric_error_contribution IS '几何误差总贡献 (度)';

-- =============================================
-- 更新视图
-- =============================================

CREATE OR REPLACE VIEW latest_sensor_data AS
SELECT DISTINCT ON (instrument_id, axis_name)
    id, instrument_id, axis_name, timestamp,
    rotational_speed, friction_torque, wear_depth,
    pointing_error_az, pointing_error_alt, temperature,
    load_radial, load_axial, calculated_at
FROM sensor_data
ORDER BY instrument_id, axis_name, timestamp DESC;

CREATE OR REPLACE VIEW active_alerts AS
SELECT * FROM alerts
WHERE is_acknowledged = FALSE
ORDER BY created_at DESC;

-- =============================================
-- 更新函数
-- =============================================

CREATE OR REPLACE FUNCTION calculate_total_pointing_error(
    az_err NUMERIC,
    alt_err NUMERIC,
    geometric_err NUMERIC DEFAULT 0
) RETURNS NUMERIC AS $$
BEGIN
    RETURN SQRT(
        (az_err + geometric_err * 0.5) * (az_err + geometric_err * 0.5) +
        (alt_err + geometric_err * 0.5) * (alt_err + geometric_err * 0.5)
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION calculate_wear_rate(
    friction_coeff NUMERIC,
    load NUMERIC,
    sliding_distance NUMERIC,
    hardness NUMERIC,
    wear_coeff NUMERIC,
    material_pair VARCHAR DEFAULT 'BRONZE_CASTIRON',
    lambda_ratio NUMERIC DEFAULT 1.0
) RETURNS NUMERIC AS $$
DECLARE
    material_correction NUMERIC;
    lubrication_correction NUMERIC;
BEGIN
    -- 根据材料对修正磨损系数
    material_correction := CASE material_pair
        WHEN 'BRONZE_CASTIRON' THEN 1.0
        WHEN 'BRONZE_STEEL' THEN 0.7
        WHEN 'CASTIRON_CASTIRON' THEN 1.4
        WHEN 'BRASS_STEEL' THEN 0.85
        ELSE 1.0
    END;

    -- 根据润滑状态修正磨损系数
    lubrication_correction := CASE
        WHEN lambda_ratio < 0.5 THEN 10.0
        WHEN lambda_ratio < 1.5 THEN 2.0
        WHEN lambda_ratio < 3.0 THEN 0.5
        ELSE 0.1
    END;

    RETURN wear_coeff * material_correction * lubrication_correction *
           friction_coeff * load * sliding_distance / hardness;
END;
$$ LANGUAGE plpgsql;

COMMIT;
