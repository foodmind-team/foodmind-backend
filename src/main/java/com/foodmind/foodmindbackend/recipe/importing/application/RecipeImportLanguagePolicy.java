package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RecipeImportLanguagePolicy {
    public static final String ENGLISH_ONLY_MESSAGE =
            "Please use English only. Chinese or mixed-language input is not supported.";

    private RecipeImportLanguagePolicy() {
    }

    public static String validateText(String text) {
        if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > 100_000) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    List.of(new ApiFieldError("text", "SIZE", "Enter between 1 and 100,000 bytes.")));
        }
        requireEnglish(text, "text");
        return text.trim();
    }

    public static String validateAnswer(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Every recipe-import answer must contain a value.");
        }
        requireEnglish(value, "answers");
        return value.trim();
    }

    private static void requireEnglish(String value, String field) {
        boolean containsNonLatinLetter = value.codePoints()
                .filter(Character::isLetter)
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN);
        if (containsNonLatinLetter) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ENGLISH_ONLY_MESSAGE,
                    List.of(new ApiFieldError(field, "ENGLISH_ONLY", ENGLISH_ONLY_MESSAGE)));
        }
    }
}
