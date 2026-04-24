package com.bank.account.utils;

import com.bank.account.exception.custom_exceptions.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .setTimeZone(TimeZone.getTimeZone("UTC"));

    public static String convertToJson(Object obj) throws JsonProcessingException {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonProcessingException("Failed to convert object to JSON: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while converting object to JSON", e);
        }
    }

    public static String extractHeader(ConsumerRecord<?, ?> record, String headerKey) {
        final Header header = record.headers().lastHeader(headerKey);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}

