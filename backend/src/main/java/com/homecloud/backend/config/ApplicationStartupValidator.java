package com.homecloud.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ApplicationStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupValidator.class);

    private static final Set<String> INSECURE_PASSWORDS = Set.of(
            "change_me", "change_me_to_secure_password", "password", "admin", "root", "123456", "Your_Secret"
    );

    private static final Set<String> INSECURE_JWT_SECRETS = Set.of(
            "change_me", "secret", "TestSecretKeyForTestingJwtAuthentication1234567890",
            "your_secure_random_jwt_secret_min_32_characters_long_12345"
    );

    private final String jwtSecret;
    private final String dbPassword;
    private final Environment environment;

    public ApplicationStartupValidator(
            @Value("${homecloud.jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String dbPassword,
            Environment environment) {
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            log.info("Test profile active - skipping production security startup validations");
            return;
        }

        // 1. Validate JWT Secret
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("FATAL: JWT_SECRET must be configured and cannot be empty.");
        }
        if (jwtSecret.trim().length() < 32) {
            throw new IllegalStateException("FATAL: JWT_SECRET must be at least 32 characters long for cryptographic security.");
        }
        if (INSECURE_JWT_SECRETS.contains(jwtSecret.trim())) {
            throw new IllegalStateException("FATAL: Insecure default JWT_SECRET detected! You must specify a unique secret key.");
        }

        // 2. Validate Database Password
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("FATAL: Database password (DB_PASSWORD / POSTGRES_PASSWORD) must be configured and cannot be empty.");
        }
        if (INSECURE_PASSWORDS.contains(dbPassword.trim())) {
            throw new IllegalStateException("FATAL: Insecure database password detected! Change the default password in .env before running.");
        }

        log.info("Startup configuration validated successfully (production security constraints satisfied).");
    }
}
