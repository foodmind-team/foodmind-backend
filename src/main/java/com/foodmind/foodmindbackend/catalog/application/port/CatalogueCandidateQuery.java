package com.foodmind.foodmindbackend.catalog.application.port;

import com.foodmind.foodmindbackend.catalog.domain.OfferingCandidate;
import com.foodmind.foodmindbackend.catalog.domain.RecipeCandidate;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

public interface CatalogueCandidateQuery {

    List<OfferingCandidate> activeOfferingCandidates(int limit);

    List<RecipeCandidate> controlledRecipeCandidates(int limit);
}
