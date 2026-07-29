package com.foodmind.foodmindbackend.group.application;

import com.foodmind.foodmindbackend.group.application.port.GroupFeedQuery;
import com.foodmind.foodmindbackend.group.domain.GroupFeedCursor;
import com.foodmind.foodmindbackend.group.domain.GroupFeedPage;
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
public class GroupFeedService {

    private final GroupMembershipPolicy membershipPolicy;
    private final GroupFeedQuery groupFeedQuery;

    public GroupFeedService(GroupMembershipPolicy membershipPolicy, GroupFeedQuery groupFeedQuery) {
        this.membershipPolicy = membershipPolicy;
        this.groupFeedQuery = groupFeedQuery;
    }

    @Transactional(readOnly = true)
    public GroupFeedPage get(UUID actorUserId, UUID groupId, String after, int limit) {
        membershipPolicy.requireActiveMember(actorUserId, groupId);
        return groupFeedQuery.findVisibleEvents(actorUserId, groupId, GroupFeedCursor.after(after), Math.max(1, Math.min(limit, 50)));
    }
}
