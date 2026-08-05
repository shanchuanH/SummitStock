package com.example.portfolio;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
@ConfigurationPropertiesScan
public class PortfolioApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(PortfolioApplication.class);
        var runtimeMode = System.getenv().getOrDefault("PORTFOLIO_RUNTIME_MODE", "api");
        if (runtimeMode.toLowerCase(Locale.ROOT).matches("worker|migration")) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        var context = application.run(args);
        if (runtimeMode.equalsIgnoreCase("migration")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
