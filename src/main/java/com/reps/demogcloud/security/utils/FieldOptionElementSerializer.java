package com.reps.demogcloud.security.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.reps.demogcloud.models.punishment.FieldOptionElement;

import java.io.IOException;

public class FieldOptionElementSerializer extends JsonSerializer<FieldOptionElement> {
    @Override
    public void serialize(FieldOptionElement fieldOptionElement, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField("value", fieldOptionElement.getValue());
        jsonGenerator.writeStringField("text", fieldOptionElement.getText());
        // Include other fields if necessary
        jsonGenerator.writeEndObject();
    }
}
