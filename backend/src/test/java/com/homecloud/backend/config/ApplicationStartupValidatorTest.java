package com.homecloud.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationStartupValidatorTest {

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Valid production configuration passes validation")
    void validConfiguration_passes() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "SuperSecureProdJwtSecretWithMoreThan32CharsRequiredForSecurity!",
                "ValidStrongDatabasePassword123#",
                environment
        );

        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    @DisplayName("Test profile skips production validation")
    void testProfile_skipsValidation() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "",
                "",
                environment
        );

        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    @DisplayName("Empty JWT secret throws IllegalStateException")
    void emptyJwtSecret_throws() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "",
                "ValidStrongDatabasePassword123#",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("JWT_SECRET must be configured"));
    }

    @Test
    @DisplayName("Short JWT secret (< 32 chars) throws IllegalStateException")
    void shortJwtSecret_throws() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "ShortSecret123",
                "ValidStrongDatabasePassword123#",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("at least 32 characters long"));
    }

    @Test
    @DisplayName("Insecure default JWT secret throws IllegalStateException")
    void insecureDefaultJwtSecret_throws() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "TestSecretKeyForTestingJwtAuthentication1234567890",
                "ValidStrongDatabasePassword123#",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("Insecure default JWT_SECRET detected"));
    }

    @Test
    @DisplayName("Insecure default DB password throws IllegalStateException")
    void insecureDbPassword_throws() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        ApplicationStartupValidator validator = new ApplicationStartupValidator(
                "SuperSecureProdJwtSecretWithMoreThan32CharsRequiredForSecurity!",
                "change_me_to_secure_password",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));
        assertTrue(ex.getMessage().contains("Insecure database password detected"));
    }
}
