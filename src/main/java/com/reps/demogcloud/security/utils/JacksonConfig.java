package com.reps.demogcloud.security.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        SimpleModule module = new SimpleModule();
        mapper.registerModule(new JavaTimeModule());
        module.addSerializer(FieldOptionElement.class, new FieldOptionElementSerializer());
        mapper.registerModule(module);
        return mapper;
    }
}
