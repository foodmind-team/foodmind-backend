package com.foodmind.foodmindbackend.search.api.internal.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record InternalResolveReferencesRequest(
        UUID sessionId,
        @Size(max = 20)
        List<UUID> referenceIds) {
}
