package com.foodmind.foodmindbackend.cooking.application.port;

import com.foodmind.foodmindbackend.cooking.domain.CookingPlanRequestContext;
import com.foodmind.foodmindbackend.cooking.domain.CookingPreferenceRules;
import com.foodmind.foodmindbackend.cooking.domain.RecipeCandidate;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

public interface CookingContextQuery {

    CookingPreferenceRules preferenceRules(UUID userId);

    List<RecipeCandidate> controlledCandidates(UUID userId, CookingPlanRequestContext request, CookingPreferenceRules mergedRules);
}
