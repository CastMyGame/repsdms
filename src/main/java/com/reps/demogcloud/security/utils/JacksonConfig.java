package com.reps.demogcloud.security.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reps.demogcloud.models.punishment.FieldOptionElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ✅ Add Java time support (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // ✅ Critical for java.time: serialize as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Your custom serializer stays the same
        SimpleModule module = new SimpleModule();
        module.addSerializer(FieldOptionElement.class, new FieldOptionElementSerializer());
        mapper.registerModule(module);

        return mapper;
    }
}
