package com.foodmind.foodmindbackend.recommendation.infrastructure.export;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

@Component
public class TrainingFeatureSchemaRegistry {

    private static final String FEATURE_SCHEMA_VERSION = "recommendation-features-v1";
    private static final List<String> ALLOW_LIST = List.of(
            "mealType",
            "cuisineCode",
            "area",
            "priceAmount",
            "currency",
            "spiceLevel",
            "available",
            "cleanlinessScore",
            "dietaryTagCodes",
            "allergenCodes",
            "wantToTry",
            "personalRecordCount",
            "personalAverageRating",
            "groupRecordCount",
            "groupAverageRating",
            "distanceKm");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "userid",
            "user_id",
            "email",
            "comment",
            "chat",
            "token",
            "latitude",
            "longitude",
            "location",
            "placemealid",
            "place_meal_id",
            "mealid",
            "meal_id",
            "placeid",
            "place_id");

    public Map<String, Object> require(String featureSchemaVersion, Map<String, Object> rawFeatureSnapshot) {
        if (!FEATURE_SCHEMA_VERSION.equals(featureSchemaVersion)) {
            throw new IllegalArgumentException("Unknown feature schema version: " + featureSchemaVersion);
        }
        if (rawFeatureSnapshot == null) {
            throw new IllegalArgumentException("Raw feature snapshot is required for " + featureSchemaVersion);
        }
        rejectForbiddenKeys("", rawFeatureSnapshot);
        if (!rawFeatureSnapshot.keySet().equals(Set.copyOf(ALLOW_LIST))) {
            List<String> unexpected = rawFeatureSnapshot.keySet().stream()
                    .filter(key -> !ALLOW_LIST.contains(key))
                    .sorted()
                    .toList();
            List<String> missing = ALLOW_LIST.stream()
                    .filter(key -> !rawFeatureSnapshot.containsKey(key))
                    .toList();
            throw new IllegalArgumentException("Feature snapshot keys mismatch. missing=" + missing + ", unexpected=" + unexpected);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mealType", optionalString(rawFeatureSnapshot, "mealType"));
        output.put("cuisineCode", optionalString(rawFeatureSnapshot, "cuisineCode"));
        output.put("area", optionalString(rawFeatureSnapshot, "area"));
        output.put("priceAmount", optionalDecimal(rawFeatureSnapshot, "priceAmount", BigDecimal.ZERO, new BigDecimal("10000")));
        output.put("currency", optionalCurrency(rawFeatureSnapshot, "currency"));
        output.put("spiceLevel", optionalInteger(rawFeatureSnapshot, "spiceLevel", 0, 5));
        output.put("available", optionalBoolean(rawFeatureSnapshot, "available"));
        output.put("cleanlinessScore", optionalDecimal(rawFeatureSnapshot, "cleanlinessScore", BigDecimal.ZERO, new BigDecimal("5")));
        output.put("dietaryTagCodes", stringList(rawFeatureSnapshot, "dietaryTagCodes"));
        output.put("allergenCodes", stringList(rawFeatureSnapshot, "allergenCodes"));
        output.put("wantToTry", optionalBoolean(rawFeatureSnapshot, "wantToTry"));
        output.put("personalRecordCount", requiredInteger(rawFeatureSnapshot, "personalRecordCount", 0, 100000));
        output.put("personalAverageRating", optionalDecimal(rawFeatureSnapshot, "personalAverageRating", BigDecimal.ONE, new BigDecimal("5")));
        output.put("groupRecordCount", requiredInteger(rawFeatureSnapshot, "groupRecordCount", 0, 100000));
        output.put("groupAverageRating", optionalDecimal(rawFeatureSnapshot, "groupAverageRating", BigDecimal.ONE, new BigDecimal("5")));
        output.put("distanceKm", optionalDecimal(rawFeatureSnapshot, "distanceKm", BigDecimal.ZERO, new BigDecimal("1000")));
        return output;
    }

    public String featureAllowListChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.join("|", ALLOW_LIST).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM.", exception);
        }
    }

    public String featureSchemaVersion() {
        return FEATURE_SCHEMA_VERSION;
    }

    public String featureAllowListVersion() {
        return "recommendation-features-v1-allowlist-2026-07-30";
    }

    @SuppressWarnings("unchecked")
    private void rejectForbiddenKeys(String path, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalised = key.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
                if (FORBIDDEN_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT)) || FORBIDDEN_KEYS.contains(normalised)) {
                    String fullPath = path.isBlank() ? key : path + "." + key;
                    throw new IllegalArgumentException("Forbidden feature key: " + fullPath);
                }
                rejectForbiddenKeys(path.isBlank() ? key : path + "." + key, entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                rejectForbiddenKeys(path + "[" + i + "]", list.get(i));
            }
        }
    }

    private String optionalString(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue)) {
            throw wrongType(key, "string");
        }
        return stringValue;
    }

    private String optionalCurrency(Map<String, Object> raw, String key) {
        String value = optionalString(raw, key);
        if (value != null && !value.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(key + " is outside category bounds.");
        }
        return value;
    }

    private Boolean optionalBoolean(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean booleanValue)) {
            throw wrongType(key, "boolean");
        }
        return booleanValue;
    }

    private Integer requiredInteger(Map<String, Object> raw, String key, int min, int max) {
        Integer value = optionalInteger(raw, key, min, max);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value;
    }

    private Integer optionalInteger(Map<String, Object> raw, String key, int min, int max) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number) || new BigDecimal(number.toString()).stripTrailingZeros().scale() > 0) {
            throw wrongType(key, "integer");
        }
        int intValue = number.intValue();
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(key + " is out of range.");
        }
        return intValue;
    }

    private BigDecimal optionalDecimal(Map<String, Object> raw, String key, BigDecimal min, BigDecimal max) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw wrongType(key, "number");
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        if (decimal.compareTo(min) < 0 || decimal.compareTo(max) > 0) {
            throw new IllegalArgumentException(key + " is out of range.");
        }
        return decimal;
    }

    private List<String> stringList(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (!(value instanceof List<?> list)) {
            throw wrongType(key, "array");
        }
        List<String> strings = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String stringItem)) {
                throw wrongType(key, "array<string>");
            }
            strings.add(stringItem);
        }
        return List.copyOf(strings);
    }

    private IllegalArgumentException wrongType(String key, String expected) {
        return new IllegalArgumentException(key + " must be " + expected + ".");
    }
}
