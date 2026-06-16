package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PointingAccuracyModel {

    private static final double ARCSEC_TO_DEG = 1.0 / 3600.0;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    public PointingAnalysisResult analyze(
            double targetRa, double targetDec,
            List<SensorData> sensorDataList,
            List<BearingConfig> bearingConfigs,
            LocalDateTime analysisTime) {

        Map<String, SensorData> sensorDataMap = new HashMap<>();
        for (SensorData data : sensorDataList) {
            sensorDataMap.put(data.getAxisName(), data);
        }

        Map<String, Double> axisErrors = new HashMap<>();
        Map<String, Double> wearContributions = new HashMap<>();
        Map<String, Double> frictionContributions = new HashMap<>();

        for (BearingConfig config : bearingConfigs) {
            String axisName = config.getAxisName();
            SensorData data = sensorDataMap.get(axisName);

            if (data != null) {
                double wearDepth = data.getWearDepth() != null ?
                        data.getWearDepth().doubleValue() : 0.0;
                double frictionTorque = data.getFrictionTorque() != null ?
                        data.getFrictionTorque().doubleValue() : 0.0;
                double maxAllowableWear = config.getMaxAllowableWear().doubleValue();

                double wearError = calculateWearInducedError(wearDepth, maxAllowableWear, config.getAxisType());
                double frictionError = calculateFrictionInducedError(frictionTorque, config);

                axisErrors.put(axisName, wearError + frictionError);
                wearContributions.put(axisName, wearError);
                frictionContributions.put(axisName, frictionError);
            } else {
                axisErrors.put(axisName, 0.0);
                wearContributions.put(axisName, 0.0);
                frictionContributions.put(axisName, 0.0);
            }
        }

        Map<String, BearingConfig> bearingConfigMap = new HashMap<>();
        for (BearingConfig config : bearingConfigs) {
            bearingConfigMap.put(config.getAxisName(), config);
        }

        GeometricErrorResult geometricError = calculateGeometricErrors(
                bearingConfigMap, targetRa, targetDec);

        double decErrorRad = targetDec * DEG_TO_RAD;
        double cosDec = Math.cos(decErrorRad);
        double sinDec = Math.sin(decErrorRad);

        double equatorialError = axisErrors.getOrDefault("赤道轴", 0.0);
        double declinationError = axisErrors.getOrDefault("赤纬轴", 0.0);
        double azimuthError = axisErrors.getOrDefault("地平经轴", 0.0);
        double altitudeError = axisErrors.getOrDefault("地平纬轴", 0.0);

        double raErrorContribution = equatorialError / Math.max(cosDec, 0.01);
        double decErrorContribution = declinationError;

        double azimuthTotal = azimuthError + equatorialError * sinDec / Math.max(cosDec, 0.01)
                + geometricError.azimuthError;
        double altitudeTotal = altitudeError + declinationError
                + geometricError.altitudeError;

        double totalPointingError = Math.sqrt(
                azimuthTotal * azimuthTotal + altitudeTotal * altitudeTotal
        );

        double uncertainty = calculateUncertainty(axisErrors, sensorDataList, geometricError);

        Map<String, Object> errorSource = new HashMap<>();
        errorSource.put("axisErrors", axisErrors);
        errorSource.put("wearContributions", wearContributions);
        errorSource.put("frictionContributions", frictionContributions);
        errorSource.put("geometricErrors", Map.of(
                "perpendicularityEquatorial", geometricError.perpendicularityEquatorial,
                "perpendicularityAltaz", geometricError.perpendicularityAltaz,
                "axialRunout", geometricError.axialRunout,
                "radialRunout", geometricError.radialRunout,
                "azimuthContribution", geometricError.azimuthError,
                "altitudeContribution", geometricError.altitudeError
        ));
        errorSource.put("raErrorComponents", Map.of(
                "equatorialAxis", equatorialError,
                "azimuthAxis", azimuthError,
                "geometric", geometricError.azimuthError,
                "totalRaError", raErrorContribution
        ));
        errorSource.put("decErrorComponents", Map.of(
                "declinationAxis", declinationError,
                "altitudeAxis", altitudeError,
                "geometric", geometricError.altitudeError,
                "totalDecError", decErrorContribution
        ));

        return new PointingAnalysisResult(
                targetRa, targetDec,
                azimuthTotal, altitudeTotal,
                totalPointingError,
                errorSource, uncertainty, geometricError
        );
    }

    private double calculateWearInducedError(double wearDepth, double maxAllowableWear, String axisType) {
        double wearRatio = Math.min(wearDepth / Math.max(maxAllowableWear, 0.001), 1.0);
        double baseErrorArcmin;

        switch (axisType) {
            case "EQUATORIAL":
                baseErrorArcmin = 8.0;
                break;
            case "DECLINATION":
                baseErrorArcmin = 6.0;
                break;
            case "AZIMUTH":
                baseErrorArcmin = 5.0;
                break;
            case "ALTITUDE":
                baseErrorArcmin = 4.0;
                break;
            default:
                baseErrorArcmin = 5.0;
        }

        return baseErrorArcmin * (0.1 + 0.9 * wearRatio) * ARCSEC_TO_DEG * 60;
    }

    private double calculateFrictionInducedError(double frictionTorque, BearingConfig config) {
        double width = config.getWidth().doubleValue() / 1000.0;
        double elasticModulus = config.getElasticModulus().doubleValue() * 1e6;

        double torsionalStiffness = elasticModulus * Math.PI * Math.pow(width, 4) / 32.0;
        double angularDeflection = frictionTorque / Math.max(torsionalStiffness, 1.0);

        return Math.abs(angularDeflection) * RAD_TO_DEG * 60 * ARCSEC_TO_DEG;
    }

    private GeometricErrorResult calculateGeometricErrors(
            Map<String, BearingConfig> bearingConfigMap,
            double targetRa, double targetDec) {

        BearingConfig equatorialBearing = bearingConfigMap.get("赤道轴");
        BearingConfig declinationBearing = bearingConfigMap.get("赤纬轴");
        BearingConfig azimuthBearing = bearingConfigMap.get("地平经轴");
        BearingConfig altitudeBearing = bearingConfigMap.get("地平纬轴");

        double perpErrorEquatorial = getGeometricError(
                equatorialBearing, declinationBearing, "perpendicularity");
        double perpErrorAltaz = getGeometricError(
                azimuthBearing, altitudeBearing, "perpendicularity");

        double axialRunout = getGeometricError(
                equatorialBearing, declinationBearing, "axialRunout");
        double radialRunout = getGeometricError(
                equatorialBearing, declinationBearing, "radialRunout");

        double[][] dcmEquatorial = buildDCMFromEuler(
                perpErrorEquatorial * DEG_TO_RAD / 2,
                0, 0);

        double raRad = targetRa * DEG_TO_RAD;
        double decRad = targetDec * DEG_TO_RAD;

        double[] starVector = {
                Math.cos(decRad) * Math.cos(raRad),
                Math.cos(decRad) * Math.sin(raRad),
                Math.sin(decRad)
        };

        double[] rotatedVector = multiplyMatrixVector(dcmEquatorial, starVector);

        double newDec = Math.asin(Math.max(-1, Math.min(1, rotatedVector[2]))) * RAD_TO_DEG;
        double newRa = Math.atan2(rotatedVector[1], rotatedVector[0]) * RAD_TO_DEG;
        if (newRa < 0) newRa += 360;

        double dRa = newRa - targetRa;
        double dDec = newDec - targetDec;

        double cosDec = Math.cos(decRad);
        double sinDec = Math.sin(decRad);

        double azimuthGeometric = dRa * cosDec + perpErrorAltaz * 0.5;
        double altitudeGeometric = dDec + axialRunout * Math.abs(sinDec)
                + radialRunout * Math.abs(cosDec);

        double geometricContribution = Math.sqrt(
                azimuthGeometric * azimuthGeometric +
                        altitudeGeometric * altitudeGeometric
        );

        return new GeometricErrorResult(
                perpErrorEquatorial, perpErrorAltaz,
                axialRunout, radialRunout,
                azimuthGeometric, altitudeGeometric,
                geometricContribution
        );
    }

    private double getGeometricError(BearingConfig bearing1, BearingConfig bearing2, String type) {
        double error1 = 0.0;
        double error2 = 0.0;

        if (bearing1 != null) {
            switch (type) {
                case "perpendicularity":
                    error1 = bearing1.getPerpendicularityError() != null ?
                            bearing1.getPerpendicularityError().doubleValue() : 0.01;
                    break;
                case "axialRunout":
                    error1 = bearing1.getAxialRunout() != null ?
                            bearing1.getAxialRunout().doubleValue() : 0.005;
                    break;
                case "radialRunout":
                    error1 = bearing1.getRadialRunout() != null ?
                            bearing1.getRadialRunout().doubleValue() : 0.003;
                    break;
            }
        }
        if (bearing2 != null) {
            switch (type) {
                case "perpendicularity":
                    error2 = bearing2.getPerpendicularityError() != null ?
                            bearing2.getPerpendicularityError().doubleValue() : 0.01;
                    break;
                case "axialRunout":
                    error2 = bearing2.getAxialRunout() != null ?
                            bearing2.getAxialRunout().doubleValue() : 0.005;
                    break;
                case "radialRunout":
                    error2 = bearing2.getRadialRunout() != null ?
                            bearing2.getRadialRunout().doubleValue() : 0.003;
                    break;
            }
        }

        return Math.sqrt(error1 * error1 + error2 * error2);
    }

    private double[][] buildDCMFromEuler(double roll, double pitch, double yaw) {
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cy = Math.cos(yaw), sy = Math.sin(yaw);

        return new double[][]{
                {cp * cy, sr * sp * cy - cr * sy, cr * sp * cy + sr * sy},
                {cp * sy, sr * sp * sy + cr * cy, cr * sp * sy - sr * cy},
                {-sp, sr * cp, cr * cp}
        };
    }

    private double[] multiplyMatrixVector(double[][] matrix, double[] vector) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = 0;
            for (int j = 0; j < 3; j++) {
                result[i] += matrix[i][j] * vector[j];
            }
        }
        return result;
    }

    private double calculateUncertainty(Map<String, Double> axisErrors,
                                        List<SensorData> sensorDataList,
                                        GeometricErrorResult geometricError) {
        double varianceSum = 0.0;
        int count = 0;

        for (Map.Entry<String, Double> entry : axisErrors.entrySet()) {
            double error = entry.getValue();
            double systematicUncertainty = error * 0.1;
            double randomUncertainty = Math.abs(error) * 0.05;

            varianceSum += systematicUncertainty * systematicUncertainty +
                    randomUncertainty * randomUncertainty;
            count++;
        }

        double geometricUncertainty = geometricError.geometricContribution * 0.15;
        varianceSum += geometricUncertainty * geometricUncertainty;
        count++;

        return Math.sqrt(varianceSum / Math.max(count, 1));
    }

    public static class GeometricErrorResult {
        public final double perpendicularityEquatorial;
        public final double perpendicularityAltaz;
        public final double axialRunout;
        public final double radialRunout;
        public final double azimuthError;
        public final double altitudeError;
        public final double geometricContribution;

        public GeometricErrorResult(double perpEq, double perpAlt,
                                   double axial, double radial,
                                   double azErr, double altErr, double contrib) {
            this.perpendicularityEquatorial = perpEq;
            this.perpendicularityAltaz = perpAlt;
            this.axialRunout = axial;
            this.radialRunout = radial;
            this.azimuthError = azErr;
            this.altitudeError = altErr;
            this.geometricContribution = contrib;
        }
    }

    public static class PointingAnalysisResult {
        private final double targetRa;
        private final double targetDec;
        private final double azimuthError;
        private final double altitudeError;
        private final double totalPointingError;
        private final Map<String, Object> errorSource;
        private final double uncertainty;
        private final GeometricErrorResult geometricError;

        public PointingAnalysisResult(double targetRa, double targetDec,
                                      double azimuthError, double altitudeError,
                                      double totalPointingError,
                                      Map<String, Object> errorSource,
                                      double uncertainty,
                                      GeometricErrorResult geometricError) {
            this.targetRa = targetRa;
            this.targetDec = targetDec;
            this.azimuthError = azimuthError;
            this.altitudeError = altitudeError;
            this.totalPointingError = totalPointingError;
            this.errorSource = errorSource;
            this.uncertainty = uncertainty;
            this.geometricError = geometricError;
        }

        public PointingAnalysis toEntity(com.astrohistory.armillary.entity.ArmillaryInstrument instrument,
                                         LocalDateTime time) {
            return PointingAnalysis.builder()
                    .instrument(instrument)
                    .analysisTime(time)
                    .targetRa(BigDecimal.valueOf(targetRa).setScale(8, RoundingMode.HALF_UP))
                    .targetDec(BigDecimal.valueOf(targetDec).setScale(8, RoundingMode.HALF_UP))
                    .azimuthError(BigDecimal.valueOf(azimuthError).setScale(8, RoundingMode.HALF_UP))
                    .altitudeError(BigDecimal.valueOf(altitudeError).setScale(8, RoundingMode.HALF_UP))
                    .totalPointingError(BigDecimal.valueOf(totalPointingError).setScale(8, RoundingMode.HALF_UP))
                    .perpendicularityErrorEquatorial(BigDecimal.valueOf(
                            geometricError.perpendicularityEquatorial).setScale(8, RoundingMode.HALF_UP))
                    .perpendicularityErrorAltaz(BigDecimal.valueOf(
                            geometricError.perpendicularityAltaz).setScale(8, RoundingMode.HALF_UP))
                    .axialRunoutError(BigDecimal.valueOf(
                            geometricError.axialRunout).setScale(8, RoundingMode.HALF_UP))
                    .radialRunoutError(BigDecimal.valueOf(
                            geometricError.radialRunout).setScale(8, RoundingMode.HALF_UP))
                    .geometricErrorContribution(BigDecimal.valueOf(
                            geometricError.geometricContribution).setScale(8, RoundingMode.HALF_UP))
                    .raErrorSource(errorSource)
                    .errorUncertainty(BigDecimal.valueOf(uncertainty).setScale(8, RoundingMode.HALF_UP))
                    .build();
        }

        public double getTargetRa() { return targetRa; }
        public double getTargetDec() { return targetDec; }
        public double getAzimuthError() { return azimuthError; }
        public double getAltitudeError() { return altitudeError; }
        public double getTotalPointingError() { return totalPointingError; }
        public Map<String, Object> getErrorSource() { return errorSource; }
        public double getUncertainty() { return uncertainty; }
        public GeometricErrorResult getGeometricError() { return geometricError; }
    }
}
