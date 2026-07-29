package com.foodmind.foodmindbackend.group.api.request;

import com.foodmind.foodmindbackend.group.application.GroupService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record CreateGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description) {

    public GroupService.Command toCommand() {
        return new GroupService.Command(name, description);
    }
}
