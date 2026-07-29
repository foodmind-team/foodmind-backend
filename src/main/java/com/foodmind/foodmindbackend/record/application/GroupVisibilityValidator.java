package com.foodmind.foodmindbackend.record.application;

import com.foodmind.foodmindbackend.group.application.GroupMembershipPolicy;
import com.foodmind.foodmindbackend.record.domain.FoodRecord;
import com.foodmind.foodmindbackend.record.domain.FoodRecordVisibility;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Component
public class GroupVisibilityValidator {

    private final GroupMembershipPolicy groupMembershipPolicy;

    public GroupVisibilityValidator(GroupMembershipPolicy groupMembershipPolicy) {
        this.groupMembershipPolicy = groupMembershipPolicy;
    }

    public void validateCreate(UUID actorUserId, FoodRecordVisibility visibility, UUID groupId) {
        if (visibility == FoodRecordVisibility.GROUP) {
            groupMembershipPolicy.requireActiveMember(actorUserId, groupId);
        }
    }

    public void validateUpdate(UUID actorUserId, FoodRecord current, FoodRecordVisibility visibility, UUID groupId) {
        if (visibility != FoodRecordVisibility.GROUP) {
            return;
        }
        boolean retainingExistingGroup = current.visibility() == FoodRecordVisibility.GROUP
                && current.groupId() != null
                && current.groupId().equals(groupId);
        if (!retainingExistingGroup) {
            groupMembershipPolicy.requireActiveMember(actorUserId, groupId);
        }
    }
}
