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
@ConfigurationProperties(prefix = "geometric-errors")
@PropertySources({
    @PropertySource(value = "classpath:geometric-errors.yml", factory = YamlPropertySourceFactory.class)
})
public class GeometricErrorProperties {

    private DefaultAxisErrors defaultAxisErrors = new DefaultAxisErrors();
    private Map<String, AxisBaseline> axisErrorBaselines = new HashMap<>();
    private ErrorPropagation errorPropagation = new ErrorPropagation();
    private UncertaintyAssessment uncertaintyAssessment = new UncertaintyAssessment();
    private SkyRegionAmplification skyRegionAmplification = new SkyRegionAmplification();
    private Map<String, Double> errorSourceWeights = new HashMap<>();

    @Data
    public static class DefaultAxisErrors {
        private double perpendicularityEquatorial = 0.01;
        private double perpendicularityAltaz = 0.01;
        private double axialRunout = 0.005;
        private double radialRunout = 0.003;
    }

    @Data
    public static class AxisBaseline {
        private double wearBaseErrorArcmin = 5.0;
        private String description;
    }

    @Data
    public static class ErrorPropagation {
        private WearError wearError = new WearError();
        private FrictionError frictionError = new FrictionError();
        private GeometricProjection geometricProjection = new GeometricProjection();
        private DcmTransformation dcmTransformation = new DcmTransformation();
        private ObservingSite observingSite = new ObservingSite();
    }

    @Data
    public static class WearError {
        private double wearMinContribution = 0.1;
        private double wearMaxContribution = 1.0;
    }

    @Data
    public static class FrictionError {
        private double torsionalStiffnessFactor = 0.09817;
    }

    @Data
    public static class GeometricProjection {
        private double perpendicularityAltazAzimuthFactor = 0.5;
        private double axialRunoutSinDecWeight = 1.0;
        private double radialRunoutCosDecWeight = 1.0;
    }

    @Data
    public static class DcmTransformation {
        private double perpendicularityToRollScale = 0.5;
        private String eulerSequence = "ZXY";
    }

    @Data
    public static class ObservingSite {
        private String name = "北京古观象台";
        private double latitudeDeg = 39.9042;
        private double longitudeDeg = 116.4074;
        private double altitudeM = 43.5;
        private double defaultDeclinationDeg = 39.9;
    }

    @Data
    public static class UncertaintyAssessment {
        private double systematicFactor = 0.10;
        private double randomFactor = 0.05;
        private double geometricFactor = 0.15;
    }

    @Data
    public static class SkyRegionAmplification {
        private double highDeclinationThresholdDeg = 60.0;
        private double maxRaAmplification = 10.0;
    }
}
