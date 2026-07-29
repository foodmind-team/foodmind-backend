package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.record.application.port.HistoryQuery;
import com.foodmind.foodmindbackend.record.domain.HistoryCursor;
import com.foodmind.foodmindbackend.record.domain.HistoryEntry;
import com.foodmind.foodmindbackend.record.domain.HistoryFilter;
import com.foodmind.foodmindbackend.record.domain.HistoryPeriod;
import com.foodmind.foodmindbackend.record.domain.HistoryResult;
import com.foodmind.foodmindbackend.record.domain.HistorySourceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:11 am
 */

@Service
public class GetHistory {

    private static final int MAX_RANGE_DAYS = 366;

    private final HistoryQuery historyQuery;

    public GetHistory(HistoryQuery historyQuery) {
        this.historyQuery = historyQuery;
    }

    @Transactional(readOnly = true)
    public HistoryResult handle(UUID actorUserId, Command command) {
        ZoneId zoneId = resolveZone(actorUserId, command.timeZone());
        OffsetDateTime fromUtc = parseBoundary(command.from(), zoneId, "from");
        OffsetDateTime toUtc = parseBoundary(command.to(), zoneId, "to");
        HistoryPeriod period = command.period() == null ? HistoryPeriod.DAY : command.period();
        Set<HistorySourceType> types = parseTypes(command.types());
        HistoryCursor after = HistoryCursor.after(command.cursor());
        int size = Math.max(1, Math.min(command.size(), PageResponse.MAX_PAGE_SIZE));
        validateRange(fromUtc, toUtc);

        HistoryFilter filter = new HistoryFilter(
                fromUtc,
                toUtc,
                period,
                types,
                zoneId.getId(),
                command.groupId(),
                command.cuisineId(),
                command.placeId(),
                after,
                size + 1);
        List<HistoryEntry> queriedEntries = historyQuery.findAuthorisedHistory(actorUserId, filter);
        boolean hasNext = queriedEntries.size() > size;
        List<HistoryEntry> entries = hasNext ? queriedEntries.subList(0, size) : queriedEntries;
        String nextCursor = hasNext ? HistoryCursor.from(entries.get(entries.size() - 1)) : null;
        HistoryFilter bucketFilter = new HistoryFilter(
                fromUtc,
                toUtc,
                period,
                types,
                zoneId.getId(),
                command.groupId(),
                command.cuisineId(),
                command.placeId(),
                null,
                PageResponse.MAX_PAGE_SIZE);
        return new HistoryResult(
                fromUtc,
                toUtc,
                period,
                types,
                zoneId.getId(),
                historyQuery.findAuthorisedBuckets(actorUserId, bucketFilter),
                List.copyOf(entries),
                nextCursor);
    }

    private ZoneId resolveZone(UUID actorUserId, String explicitTimeZone) {
        String candidate = explicitTimeZone == null || explicitTimeZone.isBlank()
                ? historyQuery.userTimeZone(actorUserId).orElse("UTC")
                : explicitTimeZone.trim();
        try {
            return ZoneId.of(candidate);
        } catch (java.time.DateTimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "timeZone must be a valid IANA timezone.");
        }
    }

    private OffsetDateTime parseBoundary(String value, ZoneId zoneId, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " is required.");
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value)
                        .atStartOfDay(zoneId)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime();
            }
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " must be an ISO 8601 date or date-time.");
        }
    }

    private Set<HistorySourceType> parseTypes(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.allOf(HistorySourceType.class);
        }
        EnumSet<HistorySourceType> parsed = EnumSet.noneOf(HistorySourceType.class);
        for (String part : value.split(",")) {
            try {
                parsed.add(HistorySourceType.valueOf(part.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "types must contain FOOD, DRINK, or both.");
            }
        }
        if (parsed.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "types must contain FOOD, DRINK, or both.");
        }
        return parsed;
    }

    private void validateRange(OffsetDateTime fromUtc, OffsetDateTime toUtc) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (!fromUtc.isBefore(toUtc)) {
            errors.add(new ApiFieldError("to", "RANGE_ORDER", "to must be after from; history uses [from, to) semantics."));
        }
        if (fromUtc.until(toUtc, ChronoUnit.DAYS) > MAX_RANGE_DAYS) {
            errors.add(new ApiFieldError("to", "RANGE_MAX", "History ranges may not exceed 366 days."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
        }
    }

    public record Command(
            String from,
            String to,
            HistoryPeriod period,
            String types,
            String timeZone,
            UUID groupId,
            UUID cuisineId,
            UUID placeId,
            String cursor,
            int size) {
    }
}
