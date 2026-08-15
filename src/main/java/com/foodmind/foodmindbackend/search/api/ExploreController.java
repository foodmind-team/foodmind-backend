package com.foodmind.foodmindbackend.search.api;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.media.application.MediaReadUrlService;
import com.foodmind.foodmindbackend.search.api.response.ExplorePageResponse;
import com.foodmind.foodmindbackend.search.application.ExploreContent;
import com.foodmind.foodmindbackend.search.domain.ExploreCursor;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Validated
@RestController
@RequestMapping("/api/v1/explore")
public class ExploreController {

    private final ExploreContent exploreContent;
    private final MediaReadUrlService mediaReadUrlService;

    public ExploreController(ExploreContent exploreContent, MediaReadUrlService mediaReadUrlService) {
        this.exploreContent = exploreContent;
        this.mediaReadUrlService = mediaReadUrlService;
    }

    @GetMapping
    ExplorePageResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String topics,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ExploreContent.MAX_PAGE_SIZE) int size) {
        rejectOffsetPage(page);
        validateTopics(topics);
        return ExplorePageResponse.from(exploreContent.explore(
                principal.id(),
                parseTypes(types),
                size,
                ExploreCursor.after(after)), mediaReadUrlService::forAuthorisedObjectKey);
    }

    private void rejectOffsetPage(int page) {
        if (page != 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Explore uses cursor pagination; page must be 0.");
        }
    }

    private void validateTopics(String topics) {
        if (topics != null && topics.length() > 200) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Explore topics must be 200 characters or fewer.");
        }
    }

    private Set<SearchSourceType> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return EnumSet.allOf(SearchSourceType.class);
        }
        EnumSet<SearchSourceType> parsed = EnumSet.noneOf(SearchSourceType.class);
        Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(value -> parsed.add(parseType(value)));
        if (parsed.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "At least one supported Explore source type is required.");
        }
        return parsed;
    }

    private SearchSourceType parseType(String value) {
        try {
            return SearchSourceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unsupported Explore source type.");
        }
    }
}
