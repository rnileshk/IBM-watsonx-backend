package com.ibm.codeanalyzer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Utility methods for JSON serialization and deserialization.
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private JsonUtils() {
        // Utility class
    }

    /**
     * Create and configure the ObjectMapper instance.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Get the shared ObjectMapper instance.
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Convert an object to JSON string.
     *
     * @param object Object to serialize
     * @return JSON string
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Convert an object to pretty-printed JSON string.
     *
     * @param object Object to serialize
     * @return Pretty-printed JSON string
     */
    public static String toPrettyJson(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to pretty JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Parse JSON string to an object.
     *
     * @param json JSON string
     * @param clazz Target class
     * @param <T> Type parameter
     * @return Deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Parse JSON string to JsonNode.
     *
     * @param json JSON string
     * @return JsonNode
     */
    public static JsonNode parseJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a string is valid JSON.
     *
     * @param json String to validate
     * @return true if valid JSON
     */
    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Convert an object to a Map.
     *
     * @param object Object to convert
     * @return Map representation
     */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> toMap(Object object) {
        return OBJECT_MAPPER.convertValue(object, java.util.Map.class);
    }

    /**
     * Deep clone an object using JSON serialization.
     *
     * @param object Object to clone
     * @param clazz Target class
     * @param <T> Type parameter
     * @return Cloned object
     */
    public static <T> T deepClone(T object, Class<T> clazz) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(object);
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deep clone object: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Merge two JSON objects.
     *
     * @param mainNode Main JSON node
     * @param updateNode Update JSON node
     * @return Merged JSON node
     */
    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        if (mainNode == null) {
            return updateNode;
        }
        if (updateNode == null) {
            return mainNode;
        }

        try {
            String mainJson = OBJECT_MAPPER.writeValueAsString(mainNode);
            String updateJson = OBJECT_MAPPER.writeValueAsString(updateNode);
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> mainMap = OBJECT_MAPPER.readValue(mainJson, java.util.Map.class);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> updateMap = OBJECT_MAPPER.readValue(updateJson, java.util.Map.class);
            
            mainMap.putAll(updateMap);
            
            return OBJECT_MAPPER.valueToTree(mainMap);
        } catch (IOException e) {
            log.error("Failed to merge JSON nodes: {}", e.getMessage());
            return mainNode;
        }
    }

    /**
     * Extract a value from JSON using a path.
     *
     * @param json JSON string
     * @param path JSON path (e.g., "data.user.name")
     * @return Value as string, or null if not found
     */
    public static String extractValue(String json, String path) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            String[] parts = path.split("\\.");
            JsonNode current = root;
            
            for (String part : parts) {
                if (current == null || !current.has(part)) {
                    return null;
                }
                current = current.get(part);
            }
            
            return current != null ? current.asText() : null;
        } catch (JsonProcessingException e) {
            log.error("Failed to extract value from JSON: {}", e.getMessage());
            return null;
        }
    }
}


