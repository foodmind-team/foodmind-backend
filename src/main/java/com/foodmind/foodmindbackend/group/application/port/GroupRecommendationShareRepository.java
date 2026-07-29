package com.foodmind.foodmindbackend.group.application.port;

import com.foodmind.foodmindbackend.group.domain.GroupRecommendationShare;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public interface GroupRecommendationShareRepository {

    boolean candidateOwnedBy(UUID actorUserId, UUID candidateId);

    GroupRecommendationShare share(UUID actorUserId, UUID groupId, UUID candidateId, String message);
}
