package com.foodmind.foodmindbackend.wanttotry.api.request;

import com.foodmind.foodmindbackend.wanttotry.application.SaveWantToTry;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

public record SaveWantToTryRequest(
        @NotNull WantToTrySourceType sourceType,
        @NotNull UUID sourceId,
        @Size(max = 2000) String note) {

    public SaveWantToTry.Command toCommand() {
        return new SaveWantToTry.Command(sourceType, sourceId, note);
    }
}
