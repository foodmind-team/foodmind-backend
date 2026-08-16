package com.foodmind.foodmindbackend.preference.application;

import com.foodmind.foodmindbackend.preference.application.port.PreferenceCommandRepository;
import com.foodmind.foodmindbackend.preference.domain.PreferenceSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCookingRegion {

    private final PreferenceCommandRepository preferenceCommandRepository;

    public UpdateCookingRegion(PreferenceCommandRepository preferenceCommandRepository) {
        this.preferenceCommandRepository = preferenceCommandRepository;
    }

    @Transactional
    public PreferenceSnapshot update(UUID userId, String cookingRegion) {
        return preferenceCommandRepository.updateCookingRegion(userId, cookingRegion);
    }
}
