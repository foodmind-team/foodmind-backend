package com.foodmind.foodmindbackend.preference.application.port;

import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

public interface PreferenceQuery {

    PreferenceSnapshot snapshotForUser(UUID userId);
}
