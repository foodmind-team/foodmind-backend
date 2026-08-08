package com.foodmind.foodmindbackend.cooking.api.response;
import com.foodmind.foodmindbackend.cooking.application.ConsumeCookingPlanInventory;
import java.util.UUID;
public record CookingPlanInventoryConsumptionResponse(UUID planId, int allocationCount, int consumedAllocationCount) {
    public static CookingPlanInventoryConsumptionResponse from(ConsumeCookingPlanInventory.Result result) { return new CookingPlanInventoryConsumptionResponse(result.planId(), result.allocationCount(), result.consumedAllocationCount()); }
}
