package com.foodmind.foodmindbackend.recommendation.domain;

import com.foodmind.foodmindbackend.common.api.ApiFieldError;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:00 am
 */

public class FeedbackPolicy {

    private final Clock clock;

    public FeedbackPolicy(Clock clock) {
        this.clock = clock;
    }

    public void validatePayload(RecommendationFeedbackCommand command, RecommendationFeedbackTarget target) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (command.eventType() == null) {
            errors.add(new ApiFieldError("eventType", "REQUIRED", "Event type is required."));
        }
        if (command.eventType() == RecommendationFeedbackEventType.RERECOMMEND_REQUESTED) {
            validateSessionLevel(command, errors);
        } else {
            validateCandidateLevel(command, target, errors);
        }
        validateEventMatrix(command, errors);
        if (!errors.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    errors);
        }
    }

    public OffsetDateTime deriveTemporaryConstraint(RecommendationFeedbackCommand command) {
        if (command.eventType() != RecommendationFeedbackEventType.REJECTED || command.reasonCode() == null) {
            return null;
        }
        if (command.reasonCode().temporaryConstraintDuration() == null) {
            if (command.effectiveUntil() != null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "This rejection reason does not create a temporary constraint.");
            }
            return null;
        }
        OffsetDateTime maximum = OffsetDateTime.now(clock).plus(command.reasonCode().temporaryConstraintDuration());
        if (command.effectiveUntil() == null) {
            return maximum;
        }
        if (!command.effectiveUntil().isAfter(OffsetDateTime.now(clock))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "effectiveUntil must be in the future.");
        }
        return command.effectiveUntil().isAfter(maximum) ? maximum : command.effectiveUntil();
    }

    public Integer labelFor(RecommendationFeedbackEventType eventType) {
        if (eventType == RecommendationFeedbackEventType.ACCEPTED) {
            return 1;
        }
        if (eventType == RecommendationFeedbackEventType.REJECTED) {
            return 0;
        }
        return null;
    }

    private void validateSessionLevel(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        if (command.candidateId() != null) {
            errors.add(new ApiFieldError("candidateId", "MUST_BE_NULL", "Candidate is not accepted for session-level feedback."));
        }
    }

    private void validateCandidateLevel(
            RecommendationFeedbackCommand command,
            RecommendationFeedbackTarget target,
            List<ApiFieldError> errors) {
        if (command.candidateId() == null) {
            errors.add(new ApiFieldError("candidateId", "REQUIRED", "Candidate is required for this feedback event."));
            return;
        }
        if (target == null || !target.returnedCandidate()) {
            errors.add(new ApiFieldError("candidateId", "RETURNED_CANDIDATE_REQUIRED", "Feedback can target only returned candidates."));
        }
    }

    private void validateEventMatrix(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        if (command.eventType() == null) {
            return;
        }
        switch (command.eventType()) {
            case ACCEPTED -> requireOnlyAcceptedFields(command, errors);
            case REJECTED -> requireOnlyRejectedFields(command, errors);
            case RERECOMMEND_REQUESTED -> requireOnlyRerecommendFields(command, errors);
            case LATER_RATED -> requireOnlyLaterRatedFields(command, errors);
            case WOULD_EAT_AGAIN -> requireOnlyWouldEatAgainFields(command, errors);
        }
    }

    private void requireOnlyAcceptedFields(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        rejectPresent(command.reasonCode(), "reasonCode", errors);
        rejectPresent(command.rating(), "rating", errors);
        rejectPresent(command.booleanValue(), "booleanValue", errors);
        rejectPresent(command.resultingFoodRecordId(), "resultingFoodRecordId", errors);
    }

    private void requireOnlyRejectedFields(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        if (command.reasonCode() == null) {
            errors.add(new ApiFieldError("reasonCode", "REQUIRED", "Rejection reason is required."));
        }
        rejectPresent(command.rating(), "rating", errors);
        rejectPresent(command.booleanValue(), "booleanValue", errors);
        rejectPresent(command.resultingFoodRecordId(), "resultingFoodRecordId", errors);
    }

    private void requireOnlyRerecommendFields(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        rejectPresent(command.reasonCode(), "reasonCode", errors);
        rejectPresent(command.rating(), "rating", errors);
        rejectPresent(command.booleanValue(), "booleanValue", errors);
        rejectPresent(command.resultingFoodRecordId(), "resultingFoodRecordId", errors);
        rejectPresent(command.effectiveUntil(), "effectiveUntil", errors);
    }

    private void requireOnlyLaterRatedFields(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        rejectPresent(command.reasonCode(), "reasonCode", errors);
        if (command.rating() == null) {
            errors.add(new ApiFieldError("rating", "REQUIRED", "Rating is required."));
        } else if (command.rating().compareTo(new java.math.BigDecimal("1.0")) < 0
                || command.rating().compareTo(new java.math.BigDecimal("5.0")) > 0) {
            errors.add(new ApiFieldError("rating", "RANGE", "Rating must be between 1.0 and 5.0."));
        }
        rejectPresent(command.booleanValue(), "booleanValue", errors);
        rejectPresent(command.effectiveUntil(), "effectiveUntil", errors);
    }

    private void requireOnlyWouldEatAgainFields(RecommendationFeedbackCommand command, List<ApiFieldError> errors) {
        rejectPresent(command.reasonCode(), "reasonCode", errors);
        rejectPresent(command.rating(), "rating", errors);
        if (command.booleanValue() == null) {
            errors.add(new ApiFieldError("booleanValue", "REQUIRED", "Boolean value is required."));
        }
        rejectPresent(command.effectiveUntil(), "effectiveUntil", errors);
    }

    private void rejectPresent(Object value, String field, List<ApiFieldError> errors) {
        if (value != null) {
            errors.add(new ApiFieldError(field, "MUST_BE_NULL", "Field is not accepted for this feedback event."));
        }
    }
}
