package dev.settleup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SettleUpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettleUpApplication.class, args);
    }
}
