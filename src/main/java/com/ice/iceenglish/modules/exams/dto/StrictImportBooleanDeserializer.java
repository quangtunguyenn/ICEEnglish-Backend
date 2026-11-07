package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

public class StrictImportBooleanDeserializer extends StdScalarDeserializer<Boolean> {
    public StrictImportBooleanDeserializer() {
        super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
        if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
        return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
    }
}
