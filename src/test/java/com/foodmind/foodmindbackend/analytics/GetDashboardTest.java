package com.foodmind.foodmindbackend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.analytics.application.GetDashboard;
import com.foodmind.foodmindbackend.analytics.application.port.DashboardQuery;
import com.foodmind.foodmindbackend.analytics.domain.AnalyticsWindow;
import com.foodmind.foodmindbackend.analytics.domain.DashboardGroupBy;
import com.foodmind.foodmindbackend.analytics.domain.DashboardProjection;
import com.foodmind.foodmindbackend.common.error.ApiException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @description: Verifies dashboard local-boundary and bounded-range semantics.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:45 pm
 */

class GetDashboardTest {

    @Test
    void convertsLocalDateRangeToOneUtcBoundaryPair() {
        CapturingDashboardQuery query = new CapturingDashboardQuery();
        GetDashboard useCase = new GetDashboard(query);

        useCase.handle(UUID.randomUUID(), new GetDashboard.Command(
                "2026-07-01", "2026-07-08", DashboardGroupBy.WEEK, "Asia/Singapore"));

        assertThat(query.window.fromUtcInclusive()).isEqualTo(OffsetDateTime.parse("2026-06-30T16:00:00Z"));
        assertThat(query.window.toUtcExclusive()).isEqualTo(OffsetDateTime.parse("2026-07-07T16:00:00Z"));
        assertThat(query.window.timeZone()).isEqualTo("Asia/Singapore");
        assertThat(query.window.groupBy()).isEqualTo(DashboardGroupBy.WEEK);
    }

    @Test
    void rejectsInvertedOrUnboundedRanges() {
        GetDashboard useCase = new GetDashboard(new CapturingDashboardQuery());

        assertThatThrownBy(() -> useCase.handle(UUID.randomUUID(), new GetDashboard.Command(
                "2026-07-08", "2026-07-01", DashboardGroupBy.DAY, "UTC")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).fieldErrors().get(0).code())
                .isEqualTo("RANGE_ORDER");
    }

    private static final class CapturingDashboardQuery implements DashboardQuery {
        private AnalyticsWindow window;

        @Override
        public Optional<String> userTimeZone(UUID actorId) {
            return Optional.of("UTC");
        }

        @Override
        public DashboardProjection loadMetrics(UUID actorId, AnalyticsWindow window) {
            this.window = window;
            return new DashboardProjection(window, java.util.List.of());
        }
    }
}
