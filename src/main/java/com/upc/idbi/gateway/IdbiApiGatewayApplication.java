package com.upc.idbi.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdbiApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdbiApiGatewayApplication.class, args);
    }
}