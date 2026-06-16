package com.astrohistory.armillary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "friction-params")
@PropertySources({
    @PropertySource(value = "classpath:friction-params.yml", factory = YamlPropertySourceFactory.class)
})
public class FrictionParamsProperties {

    private Constants constants = new Constants();
    private Map<String, MaterialPairConfig> materialPairs = new HashMap<>();
    private LubricationModel lubricationModel = new LubricationModel();
    private WearCalculation wearCalculation = new WearCalculation();
    private AlertThresholds alertThresholds = new AlertThresholds();

    @Data
    public static class Constants {
        private double pi = Math.PI;
        private double gravity = 9.81;
        private double roughnessCombinedFactor = 1.41421356;
    }

    @Data
    public static class MaterialPairConfig {
        private String material1;
        private String material2;
        private double material1HardnessMpa;
        private double material2HardnessMpa;
        private double referenceWearCoefficient;
        private double hardnessExponent;
        private LubricationRegimes lubricationRegimes = new LubricationRegimes();
    }

    @Data
    public static class LubricationRegimes {
        private Regime boundary = new Regime();
        private Regime mixed1 = new Regime();
        private Regime mixed2 = new Regime();
        private Regime hydrodynamic = new Regime();
    }

    @Data
    public static class Regime {
        private double minWearCoeff;
        private double maxWearCoeff;
        private double lambdaThreshold;
    }

    @Data
    public static class LubricationModel {
        private HamrockDowson hamrockDowson = new HamrockDowson();
        private AsperityContact asperityContact = new AsperityContact();
        private FrictionCoefficient frictionCoefficient = new FrictionCoefficient();
        private ViscosityTemperature viscosityTemperature = new ViscosityTemperature();
        private PvFactor pvFactor = new PvFactor();
    }

    @Data
    public static class HamrockDowson {
        private double coefficient = 3.63;
        private double velocityExponent = 0.68;
        private double loadExponent = -0.073;
        private double materialFactorCoefficient = 0.68;
    }

    @Data
    public static class AsperityContact {
        private double fullyFluidLambda = 5.0;
        private double fullyBoundaryLambda = 0.5;
        private double transitionCenter = 3.0;
        private double transitionWidth = 1.414;
    }

    @Data
    public static class FrictionCoefficient {
        private double boundaryMin = 0.15;
        private double boundaryMax = 0.45;
        private double boundaryLambdaDecay = 1.5;
        private double hydrodynamicBase = 0.001;
        private double hydrodynamicSommerfeldFactor = 0.1;
        private double hydrodynamicMax = 0.05;
    }

    @Data
    public static class ViscosityTemperature {
        private double referenceTemperature = 40.0;
        private double decayCoefficient = 0.03;
        private double minimumVelocity = 0.001;
    }

    @Data
    public static class PvFactor {
        private double base = 1.0;
        private double frictionCoefficientWeight = 0.5;
        private double max = 3.0;
    }

    @Data
    public static class WearCalculation {
        private int archardTimeStepSeconds = 60;
        private double minimumFilmThicknessUm = 0.01;
    }

    @Data
    public static class AlertThresholds {
        private double maxWearDefaultMm = 0.1;
        private double maxPointingErrorArcmin = 1.0;
        private double frictionTorqueBaselineNm = 5.0;
        private double frictionTorqueWarningFactor = 2.0;
        private double wearApproachFactor = 0.8;
    }
}
