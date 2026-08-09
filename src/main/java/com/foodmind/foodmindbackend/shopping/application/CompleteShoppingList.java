package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.GenerateCookingPlan;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CompleteShoppingList {
    private final CheckoutShoppingList checkout;
    private final ShoppingListRepository shoppingLists;
    private final GenerateCookingPlan cookingPlans;

    public CompleteShoppingList(
            CheckoutShoppingList checkout,
            ShoppingListRepository shoppingLists,
            GenerateCookingPlan cookingPlans) {
        this.checkout = checkout;
        this.shoppingLists = shoppingLists;
        this.cookingPlans = cookingPlans;
    }

    public GenerateCookingPlan.AsyncSubmitResult handle(
            UUID userId,
            UUID shoppingListId,
            String idempotencyKey) {
        ShoppingList list = checkout.handle(userId, shoppingListId);
        if (list.continuationPlanId() != null) {
            return cookingPlans.resumeAsync(userId, list.continuationPlanId());
        }
        GenerateCookingPlan.AsyncSubmitResult result = cookingPlans.continueFromShoppingAsync(
                userId, list.sourcePlanId(), list.rootPlanId(), list.id(), idempotencyKey);
        UUID continuationPlanId = result instanceof GenerateCookingPlan.AsyncSubmitResult.Accepted accepted
                ? accepted.planId()
                : ((GenerateCookingPlan.AsyncSubmitResult.RejectedPlan) result).plan().planId();
        if (!shoppingLists.attachContinuation(userId, shoppingListId, continuationPlanId)) {
            ShoppingList current = shoppingLists.findOwned(userId, shoppingListId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            if (current.continuationPlanId() != null) {
                return cookingPlans.resumeAsync(userId, current.continuationPlanId());
            }
            throw new ApiException(ErrorCode.CONFLICT, "Shopping-list continuation could not be recorded.");
        }
        return result;
    }
}
