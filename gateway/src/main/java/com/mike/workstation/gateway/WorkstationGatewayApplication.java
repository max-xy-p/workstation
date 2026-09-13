package com.mike.workstation.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkstationGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkstationGatewayApplication.class, args);
    }
}
