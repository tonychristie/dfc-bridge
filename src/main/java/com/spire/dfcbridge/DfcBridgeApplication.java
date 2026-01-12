package com.spire.dfcbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
    info = @Info(
        title = "DFC Bridge API",
        version = "1.0.0",
        description = "REST API bridge for Documentum Foundation Classes (DFC). " +
                      "Provides HTTP endpoints for DFC operations including session management, " +
                      "DQL queries, and object manipulation.",
        contact = @Contact(
            name = "SPIRE Solutions Ltd"
        ),
        license = @License(
            name = "MIT",
            url = "https://opensource.org/licenses/MIT"
        )
    )
)
public class DfcBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DfcBridgeApplication.class, args);
    }
}
