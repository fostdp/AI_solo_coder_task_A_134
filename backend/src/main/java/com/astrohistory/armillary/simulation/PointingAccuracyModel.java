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

        double decErrorRad = targetDec * DEG_TO_RAD;
        double cosDec = Math.cos(decErrorRad);
        double sinDec = Math.sin(decErrorRad);

        double equatorialError = axisErrors.getOrDefault("赤道轴", 0.0);
        double declinationError = axisErrors.getOrDefault("赤纬轴", 0.0);
        double azimuthError = axisErrors.getOrDefault("地平经轴", 0.0);
        double altitudeError = axisErrors.getOrDefault("地平纬轴", 0.0);

        double raErrorContribution = equatorialError / Math.max(cosDec, 0.01);
        double decErrorContribution = declinationError;

        double azimuthTotal = azimuthError + equatorialError * sinDec / Math.max(cosDec, 0.01);
        double altitudeTotal = altitudeError + declinationError;

        double totalPointingError = Math.sqrt(
                azimuthTotal * azimuthTotal + altitudeTotal * altitudeTotal
        );

        double uncertainty = calculateUncertainty(axisErrors, sensorDataList);

        Map<String, Object> errorSource = new HashMap<>();
        errorSource.put("axisErrors", axisErrors);
        errorSource.put("wearContributions", wearContributions);
        errorSource.put("frictionContributions", frictionContributions);
        errorSource.put("raErrorComponents", Map.of(
                "equatorialAxis", equatorialError,
                "azimuthAxis", azimuthError,
                "totalRaError", raErrorContribution
        ));
        errorSource.put("decErrorComponents", Map.of(
                "declinationAxis", declinationError,
                "altitudeAxis", altitudeError,
                "totalDecError", decErrorContribution
        ));

        return new PointingAnalysisResult(
                targetRa, targetDec,
                azimuthTotal, altitudeTotal,
                totalPointingError,
                errorSource, uncertainty
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

    private double calculateUncertainty(Map<String, Double> axisErrors, List<SensorData> sensorDataList) {
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

        return Math.sqrt(varianceSum / Math.max(count, 1));
    }

    public static class PointingAnalysisResult {
        private final double targetRa;
        private final double targetDec;
        private final double azimuthError;
        private final double altitudeError;
        private final double totalPointingError;
        private final Map<String, Object> errorSource;
        private final double uncertainty;

        public PointingAnalysisResult(double targetRa, double targetDec,
                                      double azimuthError, double altitudeError,
                                      double totalPointingError,
                                      Map<String, Object> errorSource,
                                      double uncertainty) {
            this.targetRa = targetRa;
            this.targetDec = targetDec;
            this.azimuthError = azimuthError;
            this.altitudeError = altitudeError;
            this.totalPointingError = totalPointingError;
            this.errorSource = errorSource;
            this.uncertainty = uncertainty;
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
    }
}
