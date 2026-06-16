package com.astrohistory.armillary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArmillarySimulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArmillarySimulationApplication.class, args);
    }
}
