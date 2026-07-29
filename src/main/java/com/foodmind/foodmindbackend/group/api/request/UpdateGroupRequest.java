package com.foodmind.foodmindbackend.group.api.request;

import com.foodmind.foodmindbackend.group.application.GroupService;
import com.foodmind.foodmindbackend.group.domain.GroupStatus;
import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record UpdateGroupRequest(
        @Size(max = 120) String name,
        @Size(max = 2000) String description,
        GroupStatus status) {

    public GroupService.UpdateCommand toCommand() {
        return new GroupService.UpdateCommand(name, description, status);
    }
}
