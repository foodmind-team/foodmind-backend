package com.foodmind.foodmindbackend.preference.application;

import com.foodmind.foodmindbackend.preference.application.port.PreferenceCommandRepository;
import com.foodmind.foodmindbackend.preference.domain.PreferenceReplacement;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import com.foodmind.foodmindbackend.preference.domain.PreferenceValidation;
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
public class ReplacePreferences {

    private final PreferenceCommandRepository preferenceCommandRepository;

    public ReplacePreferences(PreferenceCommandRepository preferenceCommandRepository) {
        this.preferenceCommandRepository = preferenceCommandRepository;
    }

    @Transactional
    public PreferenceSnapshot replace(UUID userId, PreferenceReplacement replacement) {
        PreferenceValidation.validate(replacement);
        return preferenceCommandRepository.replace(userId, replacement);
    }
}
