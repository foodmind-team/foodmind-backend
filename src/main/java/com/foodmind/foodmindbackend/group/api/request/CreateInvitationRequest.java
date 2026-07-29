package com.foodmind.foodmindbackend.group.api.request;

import com.foodmind.foodmindbackend.group.application.GroupInvitationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record CreateInvitationRequest(
        @Min(1) @Max(43200) Integer expiresInMinutes,
        @Min(1) @Max(720) Integer expiresInHours,
        @Min(1) @Max(100) Integer maxUses) {

    public GroupInvitationService.Command toCommand() {
        return new GroupInvitationService.Command(expiresInMinutes, expiresInHours, maxUses);
    }
}
