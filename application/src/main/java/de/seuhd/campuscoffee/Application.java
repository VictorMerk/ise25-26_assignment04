package de.seuhd.campuscoffee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main class to start the Spring Boot application .
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = "de.seuhd.campuscoffee")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
