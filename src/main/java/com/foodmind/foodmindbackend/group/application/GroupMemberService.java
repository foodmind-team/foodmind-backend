package com.foodmind.foodmindbackend.group.application;

import com.foodmind.foodmindbackend.group.application.port.GroupRepository;
import com.foodmind.foodmindbackend.group.domain.GroupMember;
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
public class GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupMembershipPolicy membershipPolicy;

    public GroupMemberService(GroupRepository groupRepository, GroupMembershipPolicy membershipPolicy) {
        this.groupRepository = groupRepository;
        this.membershipPolicy = membershipPolicy;
    }

    @Transactional(readOnly = true)
    public List<GroupMember> list(UUID actorUserId, UUID groupId) {
        membershipPolicy.requireActiveMember(actorUserId, groupId);
        return groupRepository.listMembers(groupId);
    }

    @Transactional
    public void remove(UUID actorUserId, UUID groupId, UUID targetUserId) {
        if (!actorUserId.equals(targetUserId)) {
            membershipPolicy.requireOwner(actorUserId, groupId);
            membershipPolicy.assertLastOwnerRetained(groupId, targetUserId);
            groupRepository.removeMember(groupId, targetUserId, "REMOVED");
            return;
        }
        membershipPolicy.requireActiveMember(actorUserId, groupId);
        membershipPolicy.assertLastOwnerRetained(groupId, actorUserId);
        groupRepository.removeMember(groupId, actorUserId, "LEFT");
    }
}
