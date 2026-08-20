package com.foodmind.foodmindbackend.record.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.media.application.MediaReadUrlService;
import com.foodmind.foodmindbackend.record.api.request.CreateFoodRecordRequest;
import com.foodmind.foodmindbackend.record.api.request.UpdateFoodRecordRequest;
import com.foodmind.foodmindbackend.record.api.response.FoodRecordResponse;
import com.foodmind.foodmindbackend.record.application.CreateFoodRecord;
import com.foodmind.foodmindbackend.record.application.DeleteFoodRecord;
import com.foodmind.foodmindbackend.record.application.GetFoodRecord;
import com.foodmind.foodmindbackend.record.application.ListFoodRecords;
import com.foodmind.foodmindbackend.record.application.UpdateFoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordFilter;
import com.foodmind.foodmindbackend.record.domain.FoodRecordPage;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 10:30 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/food-records")
public class FoodRecordController {

    private final CreateFoodRecord createFoodRecord;
    private final GetFoodRecord getFoodRecord;
    private final ListFoodRecords listFoodRecords;
    private final UpdateFoodRecord updateFoodRecord;
    private final DeleteFoodRecord deleteFoodRecord;
    private final MediaReadUrlService mediaReadUrlService;

    public FoodRecordController(
            CreateFoodRecord createFoodRecord,
            GetFoodRecord getFoodRecord,
            ListFoodRecords listFoodRecords,
            UpdateFoodRecord updateFoodRecord,
            DeleteFoodRecord deleteFoodRecord,
            MediaReadUrlService mediaReadUrlService) {
        this.createFoodRecord = createFoodRecord;
        this.getFoodRecord = getFoodRecord;
        this.listFoodRecords = listFoodRecords;
        this.updateFoodRecord = updateFoodRecord;
        this.deleteFoodRecord = deleteFoodRecord;
        this.mediaReadUrlService = mediaReadUrlService;
    }

    @PostMapping
    ResponseEntity<FoodRecordResponse> create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody CreateFoodRecordRequest request) {
        FoodRecord record = createFoodRecord.create(principal.id(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/food-records/" + record.id()))
                .eTag(etag(record.version()))
                .body(response(record, principal.id()));
    }

    @GetMapping("/{id}")
    ResponseEntity<FoodRecordResponse> get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID id) {
        FoodRecord record = getFoodRecord.get(principal.id(), id);
        return ResponseEntity.ok()
                .eTag(etag(record.version()))
                .body(response(record, principal.id()));
    }

    @GetMapping
    PageResponse<FoodRecordResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID cuisineId,
            @RequestParam(required = false) UUID mealId,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) FoodRecordVisibility visibility,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxRating,
            @RequestParam(defaultValue = "occurredAt,desc") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        FoodRecordPage result = listFoodRecords.list(principal.id(), new FoodRecordFilter(
                parseFrom(from),
                parseTo(to),
                cuisineId,
                mealId,
                placeId,
                visibility,
                groupId,
                minRating,
                maxRating,
                sort,
                page,
                size));
        return PageResponse.of(result.items().stream().map(record -> response(record, principal.id())).toList(), page, size,
                result.totalItems());
    }

    @PatchMapping("/{id}")
    ResponseEntity<FoodRecordResponse> update(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateFoodRecordRequest request) {
        FoodRecord record = updateFoodRecord.update(principal.id(), id, expectedVersion(ifMatch), request.toCommand());
        return ResponseEntity.ok()
                .eTag(etag(record.version()))
                .body(response(record, principal.id()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID id) {
        deleteFoodRecord.delete(principal.id(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private FoodRecordResponse response(FoodRecord record, UUID actorUserId) {
        return FoodRecordResponse.from(record, mediaReadUrlService.forAuthorisedAsset(record.mediaAssetId()),
                record.ownerUserId().equals(actorUserId));
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    private long expectedVersion(String ifMatch) {
        String value = ifMatch == null ? "" : ifMatch.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "If-Match must contain the expected numeric version.");
        }
    }

    private OffsetDateTime parseFrom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return value.length() == 10
                    ? LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC)
                    : OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Date filters must be ISO 8601 dates or date-times.");
        }
    }

    private OffsetDateTime parseTo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return value.length() == 10
                    ? LocalDate.parse(value).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)
                    : OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Date filters must be ISO 8601 dates or date-times.");
        }
    }
}
