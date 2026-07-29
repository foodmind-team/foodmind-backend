package com.foodmind.foodmindbackend.group.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.group.application.port.GroupRecommendationShareRepository;
import com.foodmind.foodmindbackend.group.domain.GroupRecommendationShare;
import com.foodmind.foodmindbackend.group.domain.GroupValidation;
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
public class GroupRecommendationShareService {

    private final GroupMembershipPolicy membershipPolicy;
    private final GroupRecommendationShareRepository shareRepository;

    public GroupRecommendationShareService(
            GroupMembershipPolicy membershipPolicy,
            GroupRecommendationShareRepository shareRepository) {
        this.membershipPolicy = membershipPolicy;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public GroupRecommendationShare share(UUID actorUserId, UUID groupId, Command command) {
        membershipPolicy.requireActiveMember(actorUserId, groupId);
        GroupValidation.validateShareMessage(command.message());
        if (!shareRepository.candidateOwnedBy(actorUserId, command.recommendationCandidateId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return shareRepository.share(
                actorUserId,
                groupId,
                command.recommendationCandidateId(),
                GroupValidation.trimToNull(command.message()));
    }

    public record Command(UUID recommendationCandidateId, String message) {
    }
}
