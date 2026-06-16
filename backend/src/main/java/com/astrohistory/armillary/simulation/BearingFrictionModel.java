package com.astrohistory.armillary.simulation;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import org.apache.commons.math3.special.Erf;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class BearingFrictionModel {

    private static final double PI = Math.PI;
    private static final double ROUGHNESS_COMBINED = 0.4e-6;

    public FrictionSimulationResult simulate(BearingConfig config, SensorDataDTO sensorData,
                                             double accumulatedWear, LocalDateTime simulationTime) {

        double innerDiameter = config.getInnerDiameter().doubleValue() / 1000.0;
        double outerDiameter = config.getOuterDiameter().doubleValue() / 1000.0;
        double width = config.getWidth().doubleValue() / 1000.0;
        double radialClearance = config.getInitialClearance().doubleValue() / 1000.0 - accumulatedWear / 1000.0;
        double lubricantViscosity = config.getLubricantViscosity().doubleValue();
        double elasticModulus = config.getElasticModulus().doubleValue() * 1e6;
        double poissonRatio = config.getPoissonRatio().doubleValue();
        double hardness = config.getHardness().doubleValue() * 1e6;
        double wearCoefficient = config.getWearCoefficient().doubleValue();

        double rotationalSpeed = sensorData.getRotationalSpeed() != null ?
                sensorData.getRotationalSpeed().doubleValue() : 0.0;
        double radialLoad = sensorData.getLoadRadial() != null ?
                sensorData.getLoadRadial().doubleValue() * 9.81 : 500.0;
        double axialLoad = sensorData.getLoadAxial() != null ?
                sensorData.getLoadAxial().doubleValue() * 9.81 : 100.0;
        double temperature = sensorData.getTemperature() != null ?
                sensorData.getTemperature().doubleValue() : 25.0;

        double effectiveRadius = (innerDiameter + outerDiameter) / 4.0;
        double surfaceVelocity = PI * effectiveRadius * rotationalSpeed / 60.0;

        double adjustedViscosity = lubricantViscosity * Math.exp(-0.03 * (temperature - 40.0));

        double equivalentRadius = effectiveRadius;
        double equivalentModulus = 2.0 * elasticModulus / (1.0 - poissonRatio * poissonRatio);

        double hertzHalfWidth = Math.sqrt(4.0 * radialLoad * equivalentRadius / (PI * width * equivalentModulus));
        double maxContactPressure = 2.0 * radialLoad / (PI * width * hertzHalfWidth);

        double filmThickness = calculateElastohydrodynamicFilmThickness(
                adjustedViscosity, surfaceVelocity, equivalentModulus, equivalentRadius,
                radialLoad, width, poissonRatio);

        double lambdaRatio = filmThickness / ROUGHNESS_COMBINED;

        double asperityContactRatio = calculateAsperityContactRatio(lambdaRatio);
        double frictionCoefficient = calculateFrictionCoefficient(
                asperityContactRatio, adjustedViscosity, surfaceVelocity,
                maxContactPressure, hardness, lambdaRatio);

        double totalLoad = Math.sqrt(radialLoad * radialLoad + axialLoad * axialLoad);
        double frictionForce = frictionCoefficient * totalLoad;
        double frictionTorque = frictionForce * effectiveRadius;

        double wearRate = calculateArchardWearRate(
                wearCoefficient, frictionCoefficient, totalLoad,
                surfaceVelocity, hardness);

        double timeStep = 60.0;
        double wearIncrement = wearRate * timeStep;
        double totalWearDepth = accumulatedWear + wearIncrement;

        return new FrictionSimulationResult(
                lambdaRatio, filmThickness * 1e6, maxContactPressure / 1e6,
                frictionCoefficient, asperityContactRatio, wearRate,
                totalWearDepth, frictionTorque
        );
    }

    private double calculateElastohydrodynamicFilmThickness(
            double viscosity, double velocity, double eModulus,
            double radius, double load, double width, double poisson) {

        if (velocity < 0.001) {
            return 0.05e-6;
        }

        double dimensionlessLoad = load / (eModulus * radius * width);
        double dimensionlessVelocity = 6.0 * viscosity * velocity / (eModulus * radius);
        double dimensionlessMaterial = 2.0 * (1.0 - poisson * poisson) *
                Math.pow(viscosity * velocity / (eModulus * Math.sqrt(radius * dimensionlessLoad)), 0.5);

        double hamrockDowson = 3.63 * Math.pow(dimensionlessVelocity, 0.68)
                * Math.pow(1.0 / dimensionlessLoad, 0.073)
                * (1.0 - Math.exp(-0.68 * dimensionlessMaterial));

        double centralFilmThickness = hamrockDowson * radius *
                Math.pow(viscosity * velocity / (eModulus * radius), 0.5);

        return Math.max(centralFilmThickness, 0.01e-6);
    }

    private double calculateAsperityContactRatio(double lambdaRatio) {
        if (lambdaRatio > 5.0) {
            return 0.0;
        } else if (lambdaRatio < 0.5) {
            return 1.0;
        } else {
            double x = (lambdaRatio - 3.0) / Math.sqrt(2.0);
            return 0.5 * (1.0 + Erf.erf(x));
        }
    }

    private double calculateFrictionCoefficient(
            double asperityRatio, double viscosity, double velocity,
            double maxPressure, double hardness, double lambdaRatio) {

        double hydrodynamicFriction = 0.0;
        if (velocity > 0.001 && lambdaRatio > 1.0) {
            double sommerfeldNumber = viscosity * velocity / maxPressure;
            hydrodynamicFriction = 0.001 + 0.1 * Math.sqrt(sommerfeldNumber);
        }

        double boundaryFriction = 0.15 + 0.3 * (1.0 - Math.exp(-lambdaRatio / 1.5));

        return (1.0 - asperityRatio) * Math.min(hydrodynamicFriction, 0.05) +
                asperityRatio * boundaryFriction;
    }

    private double calculateArchardWearRate(
            double wearCoefficient, double frictionCoeff,
            double load, double slidingDistancePerSecond, double hardness) {

        double k = wearCoefficient;
        double w = load;
        double s = slidingDistancePerSecond;
        double h = hardness;

        return k * frictionCoeff * w * s / h;
    }

    public static class FrictionSimulationResult {
        private final double lambdaRatio;
        private final double filmThickness;
        private final double contactPressure;
        private final double frictionCoefficient;
        private final double asperityContactRatio;
        private final double wearRate;
        private final double totalWearDepth;
        private final double frictionTorque;

        public FrictionSimulationResult(double lambdaRatio, double filmThickness,
                                        double contactPressure, double frictionCoefficient,
                                        double asperityContactRatio, double wearRate,
                                        double totalWearDepth, double frictionTorque) {
            this.lambdaRatio = lambdaRatio;
            this.filmThickness = filmThickness;
            this.contactPressure = contactPressure;
            this.frictionCoefficient = frictionCoefficient;
            this.asperityContactRatio = asperityContactRatio;
            this.wearRate = wearRate;
            this.totalWearDepth = totalWearDepth;
            this.frictionTorque = frictionTorque;
        }

        public FrictionSimulation toEntity(BearingConfig config, LocalDateTime time) {
            return FrictionSimulation.builder()
                    .instrument(config.getInstrument())
                    .axisName(config.getAxisName())
                    .simulationTime(time)
                    .lambdaRatio(BigDecimal.valueOf(lambdaRatio).setScale(6, RoundingMode.HALF_UP))
                    .filmThickness(BigDecimal.valueOf(filmThickness).setScale(8, RoundingMode.HALF_UP))
                    .contactPressure(BigDecimal.valueOf(contactPressure).setScale(4, RoundingMode.HALF_UP))
                    .frictionCoefficient(BigDecimal.valueOf(frictionCoefficient).setScale(8, RoundingMode.HALF_UP))
                    .asperityContactRatio(BigDecimal.valueOf(asperityContactRatio).setScale(6, RoundingMode.HALF_UP))
                    .wearRate(BigDecimal.valueOf(wearRate).setScale(10, RoundingMode.HALF_UP))
                    .totalWearDepth(BigDecimal.valueOf(totalWearDepth).setScale(8, RoundingMode.HALF_UP))
                    .build();
        }

        public double getLambdaRatio() { return lambdaRatio; }
        public double getFilmThickness() { return filmThickness; }
        public double getContactPressure() { return contactPressure; }
        public double getFrictionCoefficient() { return frictionCoefficient; }
        public double getAsperityContactRatio() { return asperityContactRatio; }
        public double getWearRate() { return wearRate; }
        public double getTotalWearDepth() { return totalWearDepth; }
        public double getFrictionTorque() { return frictionTorque; }
    }
}
