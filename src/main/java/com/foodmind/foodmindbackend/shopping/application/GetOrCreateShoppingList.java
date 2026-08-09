package com.foodmind.foodmindbackend.shopping.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.cooking.application.port.CookingPlanRepository;
import com.foodmind.foodmindbackend.cooking.domain.CookingPlanResult;
import com.foodmind.foodmindbackend.shopping.application.port.ShoppingListRepository;
import com.foodmind.foodmindbackend.shopping.domain.ShoppingList;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetOrCreateShoppingList {
    private final ShoppingListRepository shoppingLists;
    private final CookingPlanRepository cookingPlans;
    private final Clock clock;

    public GetOrCreateShoppingList(
            ShoppingListRepository shoppingLists,
            CookingPlanRepository cookingPlans,
            Clock clock) {
        this.shoppingLists = shoppingLists;
        this.cookingPlans = cookingPlans;
        this.clock = clock;
    }

    @Transactional
    public ShoppingList handle(UUID userId, UUID sourcePlanId) {
        return shoppingLists.findOwnedBySourcePlan(userId, sourcePlanId)
                .orElseGet(() -> create(userId, sourcePlanId));
    }

    private ShoppingList create(UUID userId, UUID sourcePlanId) {
        CookingPlanResult source = cookingPlans.findOwned(userId, sourcePlanId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Cooking plan was not found."));
        if (!"NEEDS_CONFIRMATION".equals(source.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "Only a plan awaiting inventory confirmation can create a shopping list.");
        }
        CookingPlanRepository.PlanLineage lineage = cookingPlans.findLineage(userId, sourcePlanId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        CookingPlanResult root = cookingPlans.findOwned(userId, lineage.rootPlanId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        CookingPlanResult.Decision purchase = root.decisions().stream()
                .filter(decision -> "purchase".equals(decision.optionType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "The original plan does not contain a purchase decision."));
        List<PurchaseItem> purchaseItems = purchaseItems(purchase.payload());
        if (purchaseItems.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "The purchase decision contains no usable items.");
        }
        int originalServings = root.sources().stream()
                .map(CookingPlanResult.Source::targetServings)
                .filter(java.util.Objects::nonNull)
                .mapToInt(BigDecimal::intValueExact)
                .max()
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "The original plan has no serving count."));
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID listId = UUID.randomUUID();
        List<ShoppingList.Item> items = new ArrayList<>();
        for (int index = 0; index < purchaseItems.size(); index++) {
            PurchaseItem item = purchaseItems.get(index);
            items.add(new ShoppingList.Item(
                    UUID.randomUUID(), listId, index + 1, item.name(), item.quantity(), item.quantity(),
                    item.unit(), null, false, null, now, now, 0));
        }
        return shoppingLists.createIfAbsent(new ShoppingList(
                listId, userId, sourcePlanId, lineage.rootPlanId(), originalServings, null,
                "OPEN", now, now, null, 0, items));
    }

    private List<PurchaseItem> purchaseItems(Map<String, Object> payload) {
        Object raw = payload.get("items");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<PurchaseItem> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> item)) {
                continue;
            }
            String name = string(item.get("ingredient_name"));
            String unit = string(item.get("unit"));
            BigDecimal quantity = decimal(item.get("quantity"));
            if (name != null && unit != null && unit.length() <= 16
                    && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new PurchaseItem(name, quantity, unit));
            }
        }
        return List.copyOf(result);
    }

    private String string(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private BigDecimal decimal(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record PurchaseItem(String name, BigDecimal quantity, String unit) {
    }
}
