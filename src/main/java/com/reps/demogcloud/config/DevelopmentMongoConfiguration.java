package com.reps.demogcloud.config;

import com.google.cloud.spring.secretmanager.SecretManagerPropertySource;
import com.google.protobuf.ByteString;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevelopmentMongoConfiguration {
    static final String HOST = "repsdevdb.xgrmfbb.mongodb.net";
    static final String DATABASE = "repsdevdb";
    static final String SECRET_RESOURCE = "projects/repsdev31/secrets/reps-dev-mongodb-uri/versions/1";
    static final String SECRET_KEY = "sm@" + SECRET_RESOURCE;
    static final String PINNED_REFERENCE = "${" + SECRET_KEY + "}";
    private static final String PREFIX = "spring.data.mongodb.";
    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentMongoConfiguration.class);
    private static final String ATOM = "(?:[A-Za-z0-9._~-]|%[0-9A-Fa-f]{2})";
    private static final Pattern URI = Pattern.compile("\\Amongodb\\+srv://" + ATOM + "+:" + ATOM
            + "+@" + Pattern.quote(HOST) + "/" + DATABASE + "(?:\\?([!-~]+))?\\z");
    private static final Map<String, String> OPTIONS = Map.ofEntries(
            Map.entry("authsource", "admin|repsdevdb"),
            Map.entry("authmechanism", "SCRAM-SHA-(1|256)"),
            Map.entry("replicaset", "[A-Za-z0-9_-]+"),
            Map.entry("tls", "true"), Map.entry("ssl", "true"),
            Map.entry("tlsinsecure", "false"),
            Map.entry("tlsallowinvalidcertificates", "false"),
            Map.entry("tlsallowinvalidhostnames", "false"),
            Map.entry("directconnection", "false"), Map.entry("loadbalanced", "false"),
            Map.entry("retrywrites", "true|false"), Map.entry("retryreads", "true|false"),
            Map.entry("w", "majority|[0-9]+"), Map.entry("appname", ATOM + "+"),
            Map.entry("readpreference", "primary|primaryPreferred|secondary|secondaryPreferred|nearest"),
            Map.entry("serverselectiontimeoutms", "[0-9]{1,9}"),
            Map.entry("connecttimeoutms", "[0-9]{1,9}"));

    @Bean
    MongoConnectionDetails developmentMongoConnectionDetails(ConfigurableEnvironment environment) {
        checkPropertySources(environment);
        String uri = readPinnedSecret(environment);
        checkUri(uri);
        ConnectionString connection;
        try {
            // Only the pinned SRV seed can reach the driver's DNS/TXT parser.
            connection = parseConnection(uri);
        } catch (RuntimeException ignored) {
            throw refused("approved URI could not be parsed or its DNS options could not be read");
        }
        checkConnection(connection);
        return () -> connection;
    }

    ConnectionString parseConnection(String uri) {
        return new ConnectionString(uri);
    }

    @Bean(destroyMethod = "close")
    MongoClient developmentMongoClient(
            @Qualifier("developmentMongoConnectionDetails") MongoConnectionDetails details,
            MongoProperties properties) {
        ConnectionString connection = details.getConnectionString();
        checkConnection(connection);
        MongoClientSettings settings;
        try {
            // Own the settings so Boot client customizers cannot replace the validated target.
            settings = MongoClientSettings.builder().applyConnectionString(connection)
                    .uuidRepresentation(properties.getUuidRepresentation()).build();
        } catch (RuntimeException ignored) {
            throw refused("driver settings could not be constructed");
        }
        if (!settings.getSslSettings().isEnabled() || settings.getSslSettings().isInvalidHostNameAllowed()
                || settings.getSslSettings().getContext() != null) {
            throw refused("driver TLS policy is not supported");
        }
        LOG.info("Development MongoDB configuration validated: property={} source=SecretManager reference={} "
                        + "seed={} database={} tls=required hostnameVerification=required trust=JVM-default",
                PREFIX + "uri", SECRET_RESOURCE, HOST, DATABASE);
        try {
            return MongoClients.create(settings);
        } catch (RuntimeException ignored) {
            throw refused("driver client could not be created");
        }
    }

    static void checkPropertySources(ConfigurableEnvironment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod")) ||
                !PINNED_REFERENCE.equals(rawProperty(environment, "uri"))) {
            throw refused("effective URI must use the approved numeric Secret Manager reference");
        }
        for (String suffix : new String[]{"database", "host", "port", "additional-hosts", "protocol",
                "username", "password", "authentication-database", "replica-set-name", "gridfs.database",
                "ssl.enabled", "ssl.bundle"}) {
            if (rawProperty(environment, suffix) != null) {
                throw refused("alternate MongoDB target or TLS property is configured");
            }
        }
    }

    private static Object rawProperty(ConfigurableEnvironment environment, String suffix) {
        var name = ConfigurationPropertyName.of(PREFIX + suffix);
        for (var source : ConfigurationPropertySources.get(environment)) {
            var property = source.getConfigurationProperty(name);
            if (property != null) {
                return property.getValue();
            }
        }
        return null;
    }

    static String readPinnedSecret(ConfigurableEnvironment environment) {
        // Read the actual resolver, not a caller-defined property named like the secret reference.
        for (var source : environment.getPropertySources()) {
            if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)) {
                continue;
            }
            if (source instanceof SecretManagerPropertySource resolver) {
                Object value;
                try {
                    value = resolver.getProperty(SECRET_KEY);
                } catch (RuntimeException ignored) {
                    throw refused("pinned Secret Manager version could not be resolved");
                }
                if (value instanceof ByteString bytes && !bytes.isEmpty() && bytes.isValidUtf8()) {
                    return bytes.toStringUtf8();
                }
                throw refused("pinned Secret Manager version is missing, empty or invalid UTF-8");
            }
            if (source.containsProperty(SECRET_KEY)) {
                throw refused("a higher-priority property shadows the pinned secret");
            }
        }
        throw refused("Secret Manager property source is unavailable");
    }

    static void checkUri(String uri) {
        if (uri == null || uri.length() > 8192 || uri.indexOf('#') >= 0 || uri.indexOf('\\') >= 0) {
            throw refused("unsupported MongoDB URI");
        }
        var match = URI.matcher(uri);
        if (!match.matches()) {
            throw refused("URI must name the approved SRV seed and database");
        }
        if (match.group(1) == null) {
            return;
        }
        var seen = new HashSet<String>();
        for (String option : match.group(1).split("&", -1)) {
            String[] pair = option.split("=", 2);
            String key = pair[0].toLowerCase(Locale.ROOT);
            String rule = OPTIONS.get(key);
            if (pair.length != 2 || rule == null || !seen.add(key) || !pair[1].matches(rule)) {
                throw refused("unsupported, unsafe or duplicate MongoDB URI option");
            }
        }
        if (seen.contains("tls") && seen.contains("ssl")) {
            throw refused("ambiguous TLS options");
        }
    }

    static void checkConnection(ConnectionString connection) {
        if (!connection.isSrvProtocol() || !connection.getHosts().equals(java.util.List.of(HOST))
                || !DATABASE.equals(connection.getDatabase()) || !Boolean.TRUE.equals(connection.getSslEnabled())
                || Boolean.TRUE.equals(connection.getSslInvalidHostnameAllowed())
                || Boolean.TRUE.equals(connection.isDirectConnection())
                || Boolean.TRUE.equals(connection.isLoadBalanced())
                || connection.getCredential() == null
                || !java.util.Set.of("admin", DATABASE).contains(connection.getCredential().getSource())) {
            throw refused("driver resolved an unapproved MongoDB target or security setting");
        }
    }

    private static IllegalStateException refused(String reason) {
        // Driver and resolver exception text can contain the original URI.
        return new IllegalStateException("Development MongoDB startup refused: " + reason);
    }
}
