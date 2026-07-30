package com.foodmind.foodmindbackend.analytics.application;

import com.foodmind.foodmindbackend.analytics.application.port.DashboardQuery;
import com.foodmind.foodmindbackend.analytics.domain.AnalyticsWindow;
import com.foodmind.foodmindbackend.analytics.domain.DashboardGroupBy;
import com.foodmind.foodmindbackend.analytics.domain.DashboardProjection;
import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description: Validates a dashboard request and loads all metrics for identical UTC bounds.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:25 pm
 */

@Service
public class GetDashboard {

    private static final int MAX_RANGE_DAYS = 366;

    private final DashboardQuery dashboardQuery;

    public GetDashboard(DashboardQuery dashboardQuery) {
        this.dashboardQuery = dashboardQuery;
    }

    @Transactional(readOnly = true)
    public DashboardProjection handle(UUID actorId, Command command) {
        ZoneId zoneId = resolveZone(actorId, command.timeZone());
        OffsetDateTime fromUtc = parseBoundary(command.from(), zoneId, "from");
        OffsetDateTime toUtc = parseBoundary(command.to(), zoneId, "to");
        validateRange(fromUtc, toUtc);
        AnalyticsWindow window = new AnalyticsWindow(
                fromUtc,
                toUtc,
                fromUtc.atZoneSameInstant(zoneId).toLocalDate(),
                toUtc.atZoneSameInstant(zoneId).toLocalDate(),
                command.groupBy() == null ? DashboardGroupBy.DAY : command.groupBy(),
                zoneId.getId());
        return dashboardQuery.loadMetrics(actorId, window);
    }

    private ZoneId resolveZone(UUID actorId, String requestedTimeZone) {
        String value = requestedTimeZone == null || requestedTimeZone.isBlank()
                ? dashboardQuery.userTimeZone(actorId).orElse("UTC")
                : requestedTimeZone.trim();
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw validation("timeZone", "INVALID_TIME_ZONE", "timeZone must be a valid IANA timezone.");
        }
    }

    private OffsetDateTime parseBoundary(String value, ZoneId zoneId, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field, "REQUIRED", field + " is required.");
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay(zoneId).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
            }
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw validation(field, "INVALID_DATE", field + " must be an ISO 8601 date or date-time.");
        }
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (!from.isBefore(to)) {
            throw validation("to", "RANGE_ORDER", "to must be after from; dashboard uses [from, to) semantics.");
        }
        if (from.until(to, ChronoUnit.DAYS) > MAX_RANGE_DAYS) {
            throw validation("to", "RANGE_MAX", "Dashboard ranges may not exceed 366 days.");
        }
    }

    private ApiException validation(String field, String code, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(new ApiFieldError(field, code, message)));
    }

    public record Command(String from, String to, DashboardGroupBy groupBy, String timeZone) {
    }
}
