package com.foodmind.foodmindbackend.group.application.port;

import com.foodmind.foodmindbackend.group.domain.GroupFeedCursor;
import com.foodmind.foodmindbackend.group.domain.GroupFeedPage;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public interface GroupFeedQuery {

    GroupFeedPage findVisibleEvents(UUID actorUserId, UUID groupId, GroupFeedCursor after, int limit);
}
