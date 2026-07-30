package com.foodmind.foodmindbackend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.analytics.application.GetWeeklyRecap;
import com.foodmind.foodmindbackend.analytics.application.port.WeeklyRecapQuery;
import com.foodmind.foodmindbackend.analytics.domain.WeeklyRecapProjection;
import com.foodmind.foodmindbackend.common.error.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @description: Verifies Monday-only weekly recap and empty projection semantics.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:45 pm
 */

class GetWeeklyRecapTest {

    @Test
    void loadsAnEmptyProjectionForAValidMonday() {
        CapturingWeeklyRecapQuery query = new CapturingWeeklyRecapQuery();
        GetWeeklyRecap useCase = new GetWeeklyRecap(query);
        LocalDate currentMonday = LocalDate.now(ZoneId.of("Asia/Singapore")).with(DayOfWeek.MONDAY);

        WeeklyRecapProjection recap = useCase.handle(UUID.randomUUID(), currentMonday.toString());

        assertThat(recap.empty()).isTrue();
        assertThat(query.weekStart).isEqualTo(currentMonday);
        assertThat(query.timeZone).isEqualTo("Asia/Singapore");
    }

    @Test
    void rejectsNonMondayWeekStarts() {
        GetWeeklyRecap useCase = new GetWeeklyRecap(new CapturingWeeklyRecapQuery());

        assertThatThrownBy(() -> useCase.handle(UUID.randomUUID(), "2026-07-28"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).fieldErrors().get(0).code())
                .isEqualTo("MONDAY_REQUIRED");
    }

    private static final class CapturingWeeklyRecapQuery implements WeeklyRecapQuery {
        private LocalDate weekStart;
        private String timeZone;

        @Override
        public Optional<String> userTimeZone(UUID actorId) {
            return Optional.of("Asia/Singapore");
        }

        @Override
        public WeeklyRecapProjection loadWeek(UUID actorId, LocalDate weekStart, String timeZone) {
            this.weekStart = weekStart;
            this.timeZone = timeZone;
            return new WeeklyRecapProjection(weekStart, timeZone, java.util.List.of());
        }
    }
}
