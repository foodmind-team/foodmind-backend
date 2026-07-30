package com.foodmind.foodmindbackend.analytics.application.port;

import com.foodmind.foodmindbackend.analytics.domain.WeeklyRecapProjection;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * @description: Read port for the V10 owner-local weekly recap projection.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 04:35 pm
 */

public interface WeeklyRecapQuery {

    Optional<String> userTimeZone(UUID actorId);

    WeeklyRecapProjection loadWeek(UUID actorId, LocalDate weekStart, String timeZone);
}
