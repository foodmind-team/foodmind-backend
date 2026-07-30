package com.foodmind.foodmindbackend.analytics.application;

import com.foodmind.foodmindbackend.analytics.application.port.WeeklyRecapQuery;
import com.foodmind.foodmindbackend.analytics.domain.WeeklyRecapProjection;
import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description: Validates local Monday boundaries and returns the server weekly recap projection.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

@Service
public class GetWeeklyRecap {

    private static final int MAX_WEEK_AGE_DAYS = 366;

    private final WeeklyRecapQuery weeklyRecapQuery;

    public GetWeeklyRecap(WeeklyRecapQuery weeklyRecapQuery) {
        this.weeklyRecapQuery = weeklyRecapQuery;
    }

    @Transactional(readOnly = true)
    public WeeklyRecapProjection handle(UUID actorId, String requestedWeekStart) {
        ZoneId zoneId = resolveZone(actorId);
        LocalDate weekStart = parseWeekStart(requestedWeekStart);
        validateWeekRange(weekStart, zoneId);
        return weeklyRecapQuery.loadWeek(actorId, weekStart, zoneId.getId());
    }

    private ZoneId resolveZone(UUID actorId) {
        try {
            return ZoneId.of(weeklyRecapQuery.userTimeZone(actorId).orElse("UTC"));
        } catch (DateTimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    private LocalDate parseWeekStart(String value) {
        try {
            LocalDate weekStart = LocalDate.parse(value);
            if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
                throw validation("weekStart", "MONDAY_REQUIRED", "weekStart must be a Monday in the user's local timezone.");
            }
            return weekStart;
        } catch (DateTimeParseException exception) {
            throw validation("weekStart", "INVALID_DATE", "weekStart must be an ISO 8601 local date.");
        }
    }

    private void validateWeekRange(LocalDate weekStart, ZoneId zoneId) {
        LocalDate currentWeek = LocalDate.now(zoneId).with(DayOfWeek.MONDAY);
        long difference = Math.abs(ChronoUnit.DAYS.between(weekStart, currentWeek));
        if (difference > MAX_WEEK_AGE_DAYS || weekStart.isAfter(currentWeek)) {
            throw validation("weekStart", "RANGE_MAX", "weekStart must be within the last 366 days and not in a future week.");
        }
    }

    private ApiException validation(String field, String code, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(new ApiFieldError(field, code, message)));
    }
}
