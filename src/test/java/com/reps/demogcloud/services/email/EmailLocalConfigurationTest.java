package com.reps.demogcloud.services.email;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class EmailLocalConfigurationTest {
    @Test
    void manualProfileHasExplicitDatabaseCredentialsAndNoCloudImports() throws Exception {
        var config = new Properties();
        try (var input = getClass().getResourceAsStream("/application-email-local.properties")) {
            assertNotNull(input);
            config.load(input);
        }
        assertEquals("", config.getProperty("spring.config.import"));
        assertEquals("false", config.getProperty("spring.cloud.gcp.secretmanager.enabled"));
        assertEquals("127.0.0.1", config.getProperty("server.address"));
        assertEquals("${REPS_LOCAL_MONGODB_URI}", config.getProperty("spring.data.mongodb.uri"));
        assertEquals("${REPS_LOCAL_GMAIL_ENCRYPTION_KEY}", config.getProperty("reps.email.gmail.encryption-key"));
        assertEquals("${REPS_LOCAL_SMTP_PASSWORD}", config.getProperty("spring.mail.password"));
        assertEquals("http://localhost:3000/account/gmail/callback", config.getProperty("reps.email.gmail.redirect-uri"));
        for (var flag : new String[]{"reps.email.routing.phase1-enabled", "reps.email.gmail.connection-enabled",
                "reps.email.notifications.enabled", "reps.email.notifications.dispatch-enabled"}) {
            assertEquals("true", config.getProperty(flag), flag);
            for (var name : new String[]{"application.properties", "application-dev.properties", "application-prod.properties"}) {
                var defaults = new Properties();
                try (var input = getClass().getResourceAsStream("/" + name)) { defaults.load(input); }
                assertNotEquals("true", defaults.getProperty(flag), name + " must not enable " + flag);
            }
        }
    }
}
