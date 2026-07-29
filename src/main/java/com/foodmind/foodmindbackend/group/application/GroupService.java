package com.foodmind.foodmindbackend.group.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.group.application.port.GroupRepository;
import com.foodmind.foodmindbackend.group.domain.GroupStatus;
import com.foodmind.foodmindbackend.group.domain.GroupValidation;
import com.foodmind.foodmindbackend.group.domain.TrustedGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipPolicy membershipPolicy;

    public GroupService(GroupRepository groupRepository, GroupMembershipPolicy membershipPolicy) {
        this.groupRepository = groupRepository;
        this.membershipPolicy = membershipPolicy;
    }

    @Transactional
    public TrustedGroup create(UUID actorUserId, Command command) {
        GroupValidation.validateGroup(command.name(), command.description());
        return groupRepository.create(actorUserId, command.name().trim(), GroupValidation.trimToNull(command.description()));
    }

    @Transactional(readOnly = true)
    public List<TrustedGroup> list(UUID actorUserId) {
        return groupRepository.listForMember(actorUserId);
    }

    @Transactional(readOnly = true)
    public TrustedGroup get(UUID actorUserId, UUID groupId) {
        return groupRepository.findForMember(actorUserId, groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public TrustedGroup update(UUID actorUserId, UUID groupId, UpdateCommand command) {
        membershipPolicy.requireOwner(actorUserId, groupId);
        TrustedGroup current = groupRepository.findActiveForOwner(actorUserId, groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = command.name() == null ? current.name() : command.name();
        String description = command.description() == null ? current.description() : command.description();
        GroupValidation.validateGroup(name, description);
        if (command.status() == GroupStatus.ARCHIVED) {
            return groupRepository.archive(groupId);
        }
        if (command.status() != null && command.status() != GroupStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unsupported group status transition.");
        }
        return groupRepository.update(groupId, name.trim(), GroupValidation.trimToNull(description));
    }

    public record Command(String name, String description) {
    }

    public record UpdateCommand(String name, String description, GroupStatus status) {
    }
}
