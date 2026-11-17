package dev.codearena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CodeArena core. Each top-level package is an application module; Spring Modulith
 * checks that modules only use each other's public API and talk through events.
 */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class CodeArenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeArenaApplication.class, args);
    }
}
