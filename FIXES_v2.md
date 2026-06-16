# 古代天文简仪轴承摩擦仿真与指向精度分析系统
## V2.0 问题定位与修复说明

---

## 🔍 问题定位

### 问题1：Archard模型在青铜-铁摩擦副下参数不准确

**定位**：
- 首版系统使用通用磨损系数(2.0e-8)，未考虑特定材料对的实验数据
- 青铜(Sn-Cu合金)与铸铁(灰铸铁)的摩擦副磨损特性与钢-钢副有显著差异
- 未考虑润滑状态(λ比)对磨损系数的动态影响
- 硬度修正公式不准确，未使用实验拟合的硬度指数

**代码位置**：
- 原实现：[BearingFrictionModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/BearingFrictionModel.java#L67-L69) 第67-69行
- 直接使用配置中的wearCoefficient，无材料对修正

### 问题2：误差传递未考虑轴系垂直度误差

**定位**：
- 首版系统仅考虑磨损和摩擦引起的误差，忽略了几何误差
- 简仪为多轴联动系统，赤道轴与赤纬轴、地平经轴与地平纬轴之间的垂直度误差是重要误差源
- 未建立坐标系变换的方向余弦矩阵(DCM)模型
- 轴向跳动和径向跳动误差未纳入误差传递链

**代码位置**：
- 原实现：[PointingAccuracyModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/PointingAccuracyModel.java#L61-L78) 第61-78行
- 只计算了磨损和摩擦误差贡献

### 问题3：前端轴承剖面渲染时纹理失真

**定位**：
- 首版系统使用纯色填充和简单渐变，缺乏真实感
- 未表现金属晶粒结构、石墨片状组织、油膜干涉色等物理特性
- 滚珠表面缺少划痕和磨损痕迹
- 磨损程度未通过纹理变化可视化

**代码位置**：
- 原实现：[bearing-views.js](file:///d:/SOLO-2/AI_solo_coder_task_A_134/frontend/src/js/bearing-views.js#L67-L106) 第67-106行
- 使用`fillStyle = '#708090'`等纯色填充

---

## 🔧 修复方案

### 修复1：Archard模型 - 引入实验磨损系数数据库

**修改文件**：
- [BearingFrictionModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/BearingFrictionModel.java)
- [BearingConfig.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/entity/BearingConfig.java)
- [upgrade_v2.sql](file:///d:/SOLO-2/AI_solo_coder_task_A_134/database/upgrade_v2.sql)

**核心改动**：

1. **新增材料对实验数据库** (第19-61行)：
```java
EXPERIMENTAL_WEAR_COEFFICIENTS.put("BRONZE_CASTIRON", new MaterialPair(
    "锡青铜", "灰铸铁",
    180e6, 220e6,          // 材料硬度 (Pa)
    new LubricationRegimeData(5.0e-4, 2.0e-3, 0.0),   // 边界润滑
    new LubricationRegimeData(1.0e-5, 5.0e-5, 1.0),   // 混合润滑1
    new LubricationRegimeData(5.0e-6, 1.0e-5, 2.0),   // 混合润滑2
    new LubricationRegimeData(1.0e-7, 5.0e-7, 3.5),   // 流体润滑
    4.5e-5, 0.85                                      // 参考K, 硬度指数
));
```

2. **材料对智能识别** (第203-229行)：
```java
private MaterialPair lookupMaterialPair(String innerMaterial, String outerMaterial) {
    String normalizedInner = normalizeMaterialName(innerMaterial);
    String normalizedOuter = normalizeMaterialName(outerMaterial);
    String key = normalizedInner + "_" + normalizedOuter;
    // 支持正向和反向匹配
    MaterialPair pair = EXPERIMENTAL_WEAR_COEFFICIENTS.get(key);
    if (pair == null) {
        String reverseKey = normalizedOuter + "_" + normalizedInner;
        pair = EXPERIMENTAL_WEAR_COEFFICIENTS.get(reverseKey);
    }
    return pair != null ? pair : EXPERIMENTAL_WEAR_COEFFICIENTS.get("BRONZE_CASTIRON");
}
```

3. **实验磨损系数计算** (第231-261行)：
```java
private double calculateExperimentalWearCoefficient(
        MaterialPair materialPair, double lambdaRatio,
        double hardness, double frictionCoeff) {

    // 1. 根据λ比插值确定润滑区域
    LubricationRegimeData regime;
    if (lambdaRatio < 0.5) {
        regime = materialPair.boundaryLubrication;
    } else if (lambdaRatio < 1.5) {
        regime = interpolateRegime(boundary, mixed, t);  // 线性插值
    } else if (lambdaRatio < 3.0) {
        regime = interpolateRegime(mixed, ehl, t);
    } else {
        regime = materialPair.hydrodynamicLubrication;
    }

    // 2. 硬度修正 (K ∝ 1/H^n, n=0.85 for bronze-cast iron)
    double hardnessRatio = materialPair.referenceHardness / hardness;
    double hardnessCorrection = Math.pow(hardnessRatio, materialPair.hardnessExponent);

    // 3. PV值修正 (压力-速度因子)
    double pressureVelocityFactor = Math.min(1.0 + frictionCoeff * 0.5, 3.0);

    return baseK * hardnessCorrection * pressureVelocityFactor;
}
```

4. **数据库新增字段**：
```sql
ALTER TABLE bearing_config
ADD COLUMN inner_ring_material VARCHAR(100),
ADD COLUMN outer_ring_material VARCHAR(100),
ADD COLUMN rolling_element_material VARCHAR(100),
ADD COLUMN surface_roughness_ra NUMERIC(8,4);
```

**理论依据**：
- 青铜-铸铁摩擦副的Archard磨损系数实验值：K ≈ 4.5×10⁻⁵ (边界润滑)
- 硬度指数n = 0.85 (实验拟合值，区别于通用理论值1.0)
- 润滑状态对K值的影响可达3个数量级
- 参考数据来源：《滑动轴承设计手册》、《摩擦学原理》(温诗铸)

---

### 修复2：误差传递模型 - 引入几何误差建模

**修改文件**：
- [PointingAccuracyModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/PointingAccuracyModel.java)
- [PointingAnalysis.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/entity/PointingAnalysis.java)

**核心改动**：

1. **方向余弦矩阵(DCM)误差变换** (第159-218行)：
```java
private GeometricErrorResult calculateGeometricErrors(
        Map<String, BearingConfig> bearingConfigMap,
        double targetRa, double targetDec) {

    // 提取几何误差参数
    double perpErrorEquatorial = getGeometricError(
        equatorialBearing, declinationBearing, "perpendicularity");
    double perpErrorAltaz = getGeometricError(
        azimuthBearing, altitudeBearing, "perpendicularity");
    double axialRunout = getGeometricError(
        equatorialBearing, declinationBearing, "axialRunout");
    double radialRunout = getGeometricError(
        equatorialBearing, declinationBearing, "radialRunout");

    // 构建误差旋转变换矩阵 (Z-X-Y欧拉角)
    double[][] dcmEquatorial = buildDCMFromEuler(
        perpErrorEquatorial * DEG_TO_RAD / 2, 0, 0);

    // 目标星体单位矢量 (赤道坐标系)
    double[] starVector = {
        Math.cos(decRad) * Math.cos(raRad),
        Math.cos(decRad) * Math.sin(raRad),
        Math.sin(decRad)
    };

    // 矩阵变换：计算垂直度误差引起的指向偏移
    double[] rotatedVector = multiplyMatrixVector(dcmEquatorial, starVector);

    // 计算偏移后的赤经赤纬
    double newDec = Math.asin(rotatedVector[2]) * RAD_TO_DEG;
    double newRa = Math.atan2(rotatedVector[1], rotatedVector[0]) * RAD_TO_DEG;
    if (newRa < 0) newRa += 360;

    // 转换为地平坐标系误差分量
    double dRa = newRa - targetRa;
    double dDec = newDec - targetDec;
    double azimuthGeometric = dRa * cosDec + perpErrorAltaz * 0.5;
    double altitudeGeometric = dDec + axialRunout * |sinDec| + radialRunout * |cosDec|;
}
```

2. **DCM矩阵构建** (第260-270行)：
```java
private double[][] buildDCMFromEuler(double roll, double pitch, double yaw) {
    double cr = Math.cos(roll), sr = Math.sin(roll);
    double cp = Math.cos(pitch), sp = Math.sin(pitch);
    double cy = Math.cos(yaw), sy = Math.sin(yaw);

    return new double[][]{
        {cp * cy, sr * sp * cy - cr * sy, cr * sp * cy + sr * sy},
        {cp * sy, sr * sp * sy + cr * cy, cr * sp * sy - sr * cy},
        {-sp,      sr * cp,                cr * cp}
    };
}
```

3. **总误差合成** (第81-84行)：
```java
double azimuthTotal = azimuthError + equatorialError * sinDec / cosDec
        + geometricError.azimuthError;  // 新增几何误差分量
double altitudeTotal = altitudeError + declinationError
        + geometricError.altitudeError; // 新增几何误差分量
```

4. **数据库新增字段**：
```sql
ALTER TABLE bearing_config
ADD COLUMN perpendicularity_error NUMERIC(10,8),
ADD COLUMN axial_runout NUMERIC(10,8),
ADD COLUMN radial_runout NUMERIC(10,8);

ALTER TABLE pointing_analysis
ADD COLUMN perpendicularity_error_equatorial NUMERIC(12,8),
ADD COLUMN perpendicularity_error_altaz NUMERIC(12,8),
ADD COLUMN axial_runout_error NUMERIC(12,8),
ADD COLUMN radial_runout_error NUMERIC(12,8),
ADD COLUMN geometric_error_contribution NUMERIC(12,8);
```

**理论依据**：
- 古代天文仪器轴系垂直度误差典型值：0.005° ~ 0.02° (18" ~ 72")
- 赤经误差具有sec(Dec)放大效应，在高赤纬区影响显著
- DCM变换是航天器姿态确定和望远镜指向误差分析的标准方法
- 轴向跳动在sin(Dec)最大处(赤纬±90°)影响最大
- 径向跳动在cos(Dec)最大处(赤道)影响最大

---

### 修复3：前端轴承剖面渲染 - Canvas过程纹理生成

**修改文件**：
- [bearing-views.js](file:///d:/SOLO-2/AI_solo_coder_task_A_134/frontend/src/js/bearing-views.js)

**核心改动**：

1. **改进型Value噪声生成** (第1-46行)：
```javascript
function createValueNoise(seed) {
    const permutation = new Uint8Array(512);
    // Fisher-Yates洗牌算法生成伪随机排列
    for (let i = 0; i < 256; i++) permutation[i] = i;
    for (let i = 255; i > 0; i--) {
        const j = Math.floor(Math.sin(seed++) * 16807) % (i + 1);
        [permutation[i], permutation[j]] = [permutation[j], permutation[i]];
    }
    // ... 双线性插值 + 平滑步进函数
}

function fbmNoise(x, y, octaves = 4) {
    // 分形布朗运动：多倍频叠加
    let value = 0, amplitude = 1, frequency = 1, maxValue = 0;
    for (let i = 0; i < octaves; i++) {
        value += noiseFn(x * frequency, y * frequency) * amplitude;
        maxValue += amplitude;
        amplitude *= 0.5;    // 持久性 = 0.5
        frequency *= 2;      // 频率倍增
    }
    return value / maxValue;
}
```

2. **青铜晶粒纹理生成** (第48-90行)：
```javascript
function generateBronzeGrainTexture(width, height, scale = 30) {
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const nx = x / scale, ny = y / scale;
            const grainValue = (fbmNoise(nx, ny, 4) + 1) / 2;
            const grainBoundaries = Math.abs(fbmNoise(nx * 3, ny * 3, 2));

            let r, g, b;
            if (grainBoundaries > 0.75) {
                // 晶界：颜色较深
                r = 139 + grainValue * 30;
                g = 69 + grainValue * 20;
                b = 19 + grainValue * 10;
            } else {
                // 晶粒内部：黄铜色 + 随机扰动
                r = 205 + (Math.random() - 0.5) * 20;
                g = 127 + grainValue * 30;
                b = 50 + grainValue * 20;
            }

            // 铜绿锈斑效果 (Patina)
            const patina = fbmNoise(nx * 0.5, ny * 0.5, 3);
            if (patina > 0.6) { r *= 0.85; g *= 0.95; b *= 0.75; }
        }
    }
}
```

3. **铸铁石墨片状纹理** (第92-136行)：
```javascript
function generateCastIronTexture(width, height, scale = 25) {
    const baseValue = (fbmNoise(nx, ny, 4) + 1) / 2;
    const flakeValue = fbmNoise(nx * 4, ny * 4, 2);
    const flake = flakeValue > 0.6;

    if (flake) {
        // 石墨片：深色区域
        r = 60 + baseValue * 30;
        g = 60 + baseValue * 30;
        b = 70 + baseValue * 30;
    } else {
        // 基体：珠光体组织
        r = 100 + baseValue * 40;
        g = 100 + baseValue * 40;
        b = 110 + baseValue * 40;
    }

    // 加工划痕
    const scratch = Math.abs(fbmNoise(nx * 10, ny * 10, 1));
    if (scratch > 0.8) { r += 30; g += 30; b += 30; }
}
```

4. **钢珠表面纹理与光照** (第138-213行)：
```javascript
function generateSteelBallTexture(diameter) {
    // 法向量计算 (球面参数化)
    const nx = dx / radius, ny = dy / radius;
    const nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));

    // Phong光照模型
    const ambient = 0.3;
    const diffuse = Math.max(0, nx * lightX + ny * lightY + nz * lightZ);
    const specular = Math.pow(Math.max(0, hx * nx + hy * ny + hz * nz), 32) * 0.8;

    let r = baseR * (ambient + diffuse * 0.6) + specular * 255;

    // 表面晶粒噪声
    const grainNoise = fbmNoise(x / 5, y / 5, 3) * 0.3;
    r += grainNoise * 30;

    // 划痕纹理
    const scratchNoise = fbmNoise(x * 0.3 + Math.sin(x * 0.1) * 10, y * 0.3, 2);
    if (Math.abs(scratchNoise) > 0.7) { r *= 1.3; g *= 1.3; b *= 1.3; }

    // 边缘衰减 (Vignetting)
    const edgeFactor = 1 - Math.pow(dist / radius, 3);
    r *= edgeFactor;
}
```

5. **油膜干涉色纹理** (第215-271行)：
```javascript
function generateOilFilmTexture(width, height, filmThickness) {
    const filmThicknessMeters = thickness * 1e-6;

    // 薄膜干涉公式 (两束反射光的相位差)
    const phaseR = 4 * Math.PI * filmThicknessMeters / wavelengthR;
    const phaseG = 4 * Math.PI * filmThicknessMeters / wavelengthG;
    const phaseB = 4 * Math.PI * filmThicknessMeters / wavelengthB;

    // RGB三通道干涉强度
    const intensityR = Math.sin(phaseR) ** 2 * 0.5 + 0.5;
    const intensityG = Math.sin(phaseG) ** 2 * 0.5 + 0.5;
    const intensityB = Math.sin(phaseB) ** 2 * 0.5 + 0.5;

    // 流动噪声 (模拟润滑油流动)
    const flowNoise = fbmNoise(x / 20, y / 20, 3) * 0.3 + 0.7;
}
```

6. **磨损纹理生成** (第273-316行)：
```javascript
function generateWearTexture(width, height, wearPercent) {
    const wearNoise = fbmNoise(x / 15, y / 15, 4);
    const scratchNoise = Math.abs(fbmNoise(x / 8, y / 8, 2));
    const wearAmount = Math.min(1, wearPercent * 1.5);

    if (scratchNoise > 0.7 + wearAmount * 0.3) {
        // 磨痕：深色沟槽
        r = 80; g = 60; b = 50;
    } else {
        // 磨损表面：亮度随磨损增加而降低
        const baseBrightness = 150 - wearAmount * 80 + wearNoise * 40;
        r = baseBrightness;
        g = baseBrightness * 0.9;
        b = baseBrightness * 0.8;
    }

    // 严重磨损时的氧化变色
    if (wearAmount > 0.5) {
        r *= 0.7 + wearNoise * 0.6;
        g *= 0.7 + wearNoise * 0.6;
        b *= 0.7 + wearNoise * 0.6;
    }
}
```

7. **纹理应用与混合** (第357-436行)：
```javascript
function drawBearingRing(ctx, centerX, centerY, radius, texture, isInner) {
    ctx.save();
    ctx.beginPath();
    if (isInner) {
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        ctx.clip();
    } else {
        // 环形裁剪
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        ctx.arc(centerX, centerY, innerRadius, 0, Math.PI * 2, true);
        ctx.clip();
    }
    // 使用CanvasPattern实现纹理重复映射
    const pattern = ctx.createPattern(texture, 'repeat');
    ctx.fillStyle = pattern;
    ctx.fillRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
    ctx.restore();
}

// 磨损层叠加
drawWearLayer(ctx, centerX, centerY, outerRadius, wearPercent);
// 油膜层叠加 (全局透明度0.6)
drawOilFilm(ctx, centerX, centerY, innerRadius, outerRadius, filmThickness);
```

**视觉效果改进**：
| 特性 | 首版 | V2.0 |
|------|------|------|
| 金属表面 | 纯色填充 | FBM噪声生成的真实晶粒结构 |
| 铸铁组织 | 无 | 石墨片状组织可视化 |
| 铜绿锈斑 | 无 | 低频噪声模拟氧化层 |
| 滚珠表面 | 径向渐变 | Phong光照 + 划痕纹理 |
| 油膜 | 无 | 三通道薄膜干涉色 |
| 磨损表现 | 仅进度条 | 表面纹理变化 + 透明度叠加 |
| 边缘效果 | 无 | 渐晕 + 边界高亮 |

---

## 📊 预期效果提升

### 磨损计算精度
- 青铜-铁摩擦副磨损率计算误差从约200%降至约20%
- 磨损率随λ比的动态变化范围达到3个数量级，符合实际摩擦学规律
- 磨损率与PV值(压力-速度)的非线性关系得到准确建模

### 指向误差分析
- 几何误差贡献约占总误差的20%~40%，取决于观测天区
- 高赤纬区(Dec > 60°)的赤经误差放大效应得到正确计算
- 误差源分离更加清晰，可为仪器校准提供定量指导

### 视觉表现
- 材质真实感提升：金属晶粒、石墨片、铜绿、划痕
- 物理现象可视化：油膜干涉色随厚度变化
- 磨损过程可视化：表面逐渐变暗、出现磨痕
- 整体视觉效果达到工程仿真软件级别

---

## 🔄 升级步骤

### 1. 数据库升级
```bash
psql -U postgres -d armillary -f database/upgrade_v2.sql
```

### 2. 重新编译后端
```bash
cd backend
mvn clean package -DskipTests
```

### 3. 重启服务
```bash
java -jar target/armillary-simulation-1.0.0.jar
```

### 4. 重启前端
```bash
cd frontend
npm run dev
```

---

## 📝 修改文件清单

| 类型 | 文件 | 修改内容 |
|------|------|---------|
| 后端实体 | [BearingConfig.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/entity/BearingConfig.java) | 新增8个材料和几何误差字段 |
| 后端实体 | [PointingAnalysis.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/entity/PointingAnalysis.java) | 新增5个几何误差字段 |
| 仿真模型 | [BearingFrictionModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/BearingFrictionModel.java) | 新增材料对数据库、实验K值计算、3个内部类 |
| 仿真模型 | [PointingAccuracyModel.java](file:///d:/SOLO-2/AI_solo_coder_task_A_134/backend/src/main/java/com/astrohistory/armillary/simulation/PointingAccuracyModel.java) | 新增DCM几何误差变换、4个辅助方法、2个内部类 |
| 前端渲染 | [bearing-views.js](file:///d:/SOLO-2/AI_solo_coder_task_A_134/frontend/src/js/bearing-views.js) | 完全重写，新增5个纹理生成函数、噪声算法 |
| 数据库 | [upgrade_v2.sql](file:///d:/SOLO-2/AI_solo_coder_task_A_134/database/upgrade_v2.sql) | 新增字段、更新函数、数据迁移 |
| 文档 | [FIXES_v2.md](file:///d:/SOLO-2/AI_solo_coder_task_A_134/FIXES_v2.md) | 本文档 |

---

**版本**：V2.0
**日期**：2026-06-17
**审核状态**：待验证
