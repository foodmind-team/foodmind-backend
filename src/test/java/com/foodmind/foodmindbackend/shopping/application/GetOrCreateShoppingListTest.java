package com.foodmind.foodmindbackend.shopping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOrCreateShoppingListTest {

    @Test
    void reusesTheExistingListAcrossPlansInTheSameRootJourney() {
        ShoppingListRepository shoppingLists = mock(ShoppingListRepository.class);
        CookingPlanRepository cookingPlans = mock(CookingPlanRepository.class);
        UUID userId = UUID.randomUUID();
        UUID rootPlanId = UUID.randomUUID();
        UUID stalePlanId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-10T10:00:00Z");
        ShoppingList existing = new ShoppingList(
                UUID.randomUUID(), userId, UUID.randomUUID(), rootPlanId, 2, UUID.randomUUID(),
                "COMPLETED", now, now, now, 2, List.of());
        when(shoppingLists.findOwnedBySourcePlan(userId, stalePlanId)).thenReturn(Optional.empty());
        when(cookingPlans.findLineage(userId, stalePlanId))
                .thenReturn(Optional.of(new CookingPlanRepository.PlanLineage(stalePlanId, null, rootPlanId)));
        when(shoppingLists.findOwnedByRootPlan(userId, rootPlanId)).thenReturn(Optional.of(existing));

        ShoppingList result = new GetOrCreateShoppingList(shoppingLists, cookingPlans, Clock.systemUTC())
                .handle(userId, stalePlanId);

        assertThat(result).isSameAs(existing);
        verify(cookingPlans, never()).findOwned(userId, stalePlanId);
        verify(shoppingLists, never()).createIfAbsent(org.mockito.ArgumentMatchers.any());
    }
}
