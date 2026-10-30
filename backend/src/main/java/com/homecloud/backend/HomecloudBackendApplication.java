package com.homecloud.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HomecloudBackendApplication {

    public static void main(String[] args) {
        // Windows/Java may report the India timezone as Asia/Calcutta.
        // PostgreSQL in this setup expects the modern Asia/Kolkata name.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        SpringApplication.run(HomecloudBackendApplication.class, args);
    }
}
