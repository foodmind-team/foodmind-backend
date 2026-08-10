package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Validates recipe-import size and presence without restricting the input language. */
public final class RecipeImportInputPolicy {
    private RecipeImportInputPolicy() {
    }

    public static String validateText(String text) {
        if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > 100_000) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    List.of(new ApiFieldError("text", "SIZE", "Enter between 1 and 100,000 bytes.")));
        }
        return text.trim();
    }

    public static String validateAnswer(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Every recipe-import answer must contain a value.");
        }
        return value.trim();
    }
}
