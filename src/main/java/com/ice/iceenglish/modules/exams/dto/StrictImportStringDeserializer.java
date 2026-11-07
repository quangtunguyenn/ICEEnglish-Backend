package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

public class StrictImportStringDeserializer extends StdScalarDeserializer<String> {
    public StrictImportStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText();
        return (String) context.handleUnexpectedToken(String.class, parser);
    }
}
