package com.leecardo.paymentdiagnostics.api;

import com.leecardo.paymentdiagnostics.infrastructure.ai.AiModelConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(AiModelConfiguration.class)
public class AgentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApiApplication.class, args);
    }
}
