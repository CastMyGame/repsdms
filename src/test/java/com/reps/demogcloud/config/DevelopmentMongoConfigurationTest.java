package com.reps.demogcloud.config;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.spring.autoconfigure.secretmanager.GcpSecretManagerEnvironmentPostProcessor;
import com.google.cloud.spring.secretmanager.SecretManagerPropertySource;
import com.google.cloud.spring.secretmanager.SecretManagerTemplate;
import com.google.protobuf.ByteString;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.util.List;
import java.util.Map;

import static com.reps.demogcloud.config.DevelopmentMongoConfiguration.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DevelopmentMongoConfigurationTest {
    private static final String URI = "mongodb+srv://synthetic-reader:synthetic-password@" + HOST
            + "/" + DATABASE + "?retryWrites=true&w=majority&appName=synthetic";
    private static final SecretVersionName VERSION = SecretVersionName.of("repsdev31", "reps-dev-mongodb-uri", "1");

    @Configuration(proxyBeanMethods = false)
    @Profile("dev")
    static class OfflineConfiguration extends DevelopmentMongoConfiguration {
        @Override
        ConnectionString parseConnection(String uri) {
            return new ConnectionString(uri, (name, type) -> {
                assertThat(name).isEqualTo(HOST);
                assertThat(type).isEqualTo("TXT");
                return List.of("authSource=admin");
            });
        }
    }

    private static ApplicationContextRunner runner(SecretManagerServiceClient service) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
                .withUserConfiguration(OfflineConfiguration.class)
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("dev");
                    new GcpSecretManagerEnvironmentPostProcessor().postProcessEnvironment(
                            context.getEnvironment(), new SpringApplication());
                    context.getEnvironment().getPropertySources().addLast(new SecretManagerPropertySource(
                            "synthetic-secret-manager", new SecretManagerTemplate(service, () -> "other-project"),
                            () -> "other-project"));
                })
                .withPropertyValues("spring.data.mongodb.uri=" + PINNED_REFERENCE);
    }

    private static SecretManagerServiceClient secret(String value) {
        var service = mock(SecretManagerServiceClient.class);
        when(service.accessSecretVersion(VERSION)).thenReturn(AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value))).build());
        return service;
    }

    @Test
    void bootsWithExactVersionAndBuildsOnlyTheValidatedClient(CapturedOutput output) {
        var service = secret(URI);
        var client = mock(MongoClient.class);
        try (var clients = mockStatic(MongoClients.class)) {
            clients.when(() -> MongoClients.create(any(MongoClientSettings.class))).thenAnswer(call -> {
                MongoClientSettings settings = call.getArgument(0);
                assertThat(settings.getClusterSettings().getSrvHost()).isEqualTo(HOST);
                assertThat(settings.getSslSettings().isEnabled()).isTrue();
                assertThat(settings.getSslSettings().isInvalidHostNameAllowed()).isFalse();
                return client;
            });
            runner(service).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(MongoClient.class)
                        .hasSingleBean(MongoDatabaseFactory.class);
                assertThat(context.getBean(MongoClient.class)).isSameAs(client);
            });
            clients.verify(() -> MongoClients.create(any(MongoClientSettings.class)), times(1));
        }
        verify(service, atLeastOnce()).accessSecretVersion(VERSION);
        assertThat(output).contains(SECRET_RESOURCE, HOST, DATABASE)
                .doesNotContain("synthetic-reader", "synthetic-password", "mongodb+srv://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPS", "repsdms", "admin", "sslOff", "badHostname", "insecure",
            "dms", "malformed", "proxy", "duplicate"})
    void refusesBadSecretBeforeCreatingAClient(String scenario, CapturedOutput output) {
        String bad = switch (scenario) {
            case "sslOff" -> URI + "&tls=false";
            case "badHostname" -> URI + "&tlsAllowInvalidHostnames=true";
            case "insecure" -> URI + "&tlsInsecure=true";
            case "dms" -> URI.replace(HOST, "reps.risjic2.mongodb.net");
            case "malformed" -> URI.replace("synthetic-password", "synthetic-password%zz");
            case "proxy" -> URI + "&proxyHost=synthetic.invalid";
            case "duplicate" -> URI + "&retryWrites=false";
            default -> URI.replace("/" + DATABASE, "/" + scenario);
        };
        assertRefused(runner(secret(bad)), output, switch (scenario) {
            case "sslOff", "badHostname", "insecure", "proxy", "duplicate" -> "unsupported, unsafe or duplicate";
            default -> "URI must name the approved SRV seed and database";
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.data.mongodb.database=REPS",
            "spring.data.mongodb.gridfs.database=repsdms", "spring.data.mongodb.ssl.enabled=false",
            "spring.data.mongodb.ssl.bundle=unreviewed", "spring.data.mongodb.host=reps.risjic2.mongodb.net"})
    void refusesIndependentOverridesBeforeCreatingAClient(String override, CapturedOutput output) {
        assertRefused(runner(secret(URI)).withPropertyValues(override), output, "alternate MongoDB target or TLS property");
    }

    @Test
    void refusesUriOverrideEvenWhenItNamesTheApprovedTarget(CapturedOutput output) {
        assertRefused(runner(secret(URI)).withPropertyValues("spring.data.mongodb.uri=" + URI), output, "approved numeric Secret Manager reference");
    }

    @Test
    void refusesMissingVersionWithoutEchoingResolverError(CapturedOutput output) {
        var service = mock(SecretManagerServiceClient.class);
        when(service.accessSecretVersion(VERSION)).thenThrow(new IllegalStateException("untrusted error " + URI));
        assertRefused(runner(service), output, "pinned Secret Manager version could not be resolved");
    }

    @Test
    void refusesAPropertyThatImpersonatesTheSecretManagerReference(CapturedOutput output) {
        var config = runner(secret(URI)).withInitializer(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("spoof", Map.of(SECRET_KEY, URI.replace(HOST, "reps.risjic2.mongodb.net")))));
        assertRefused(config, output, "shadows the pinned secret");
    }

    @Test
    void refusesRelaxedEnvironmentDatabaseOverride(CapturedOutput output) {
        assertRefused(runner(secret(URI)).withInitializer(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.SystemEnvironmentPropertySource(
                                "synthetic-systemEnvironment", Map.of("SPRING_DATA_MONGODB_DATABASE", "REPS")))), output, "alternate MongoDB target or TLS property");
    }

    private static void assertRefused(ApplicationContextRunner config, CapturedOutput output, String expectedReason) {
        try (var clients = mockStatic(MongoClients.class)) {
            config.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("Development MongoDB startup refused")
                        .hasStackTraceContaining(expectedReason);
                var stack = new java.io.StringWriter();
                context.getStartupFailure().printStackTrace(new java.io.PrintWriter(stack));
                assertThat(stack.toString()).doesNotContain("synthetic-password", "mongodb+srv://");
            });
            clients.verifyNoInteractions();
        }
        assertThat(output).doesNotContain("synthetic-reader", "synthetic-password", "mongodb+srv://");
    }
}
