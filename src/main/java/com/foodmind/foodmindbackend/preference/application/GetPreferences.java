package com.foodmind.foodmindbackend.preference.application;

import com.foodmind.foodmindbackend.preference.application.port.PreferenceQuery;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:55 pm
 */

@Service
public class GetPreferences {

    private final PreferenceQuery preferenceQuery;

    public GetPreferences(PreferenceQuery preferenceQuery) {
        this.preferenceQuery = preferenceQuery;
    }

    @Transactional(readOnly = true)
    public PreferenceSnapshot get(UUID userId) {
        return preferenceQuery.snapshotForUser(userId);
    }
}
