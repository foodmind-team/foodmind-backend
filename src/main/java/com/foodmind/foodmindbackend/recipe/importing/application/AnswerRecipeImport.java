package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnswerRecipeImport {
    private final RecipeImportRepository repository;
    private final RecipeImportAgentPort agent;
    private final RecipeImportViews views;

    public AnswerRecipeImport(
            RecipeImportRepository repository,
            RecipeImportAgentPort agent,
            RecipeImportViews views) {
        this.repository = repository;
        this.agent = agent;
        this.views = views;
    }

    public RecipeImportView handle(
            UUID ownerUserId,
            UUID importId,
            long expectedVersion,
            List<RecipeImportAnswer> submittedAnswers) {
        RecipeImportSession session = RecipeImportSupport.owned(repository, ownerUserId, importId);
        if (session.version() != expectedVersion) {
            throw RecipeImportSupport.conflict();
        }
        if (session.status() != RecipeImportStatus.NEEDS_CLARIFICATION) {
            throw new ApiException(ErrorCode.CONFLICT, "This recipe import is not waiting for answers.");
        }
        List<RecipeImportAnswer> answers = mergeAnswers(session, submittedAnswers);
        RecipeImportAgentPort.Result result = agent.parse(importId.toString(), session.sourceText(), answers);
        RecipeImportAgentResultValidator.validate(result);
        RecipeImportSession updated = repository.updateAgentResult(
                        ownerUserId,
                        importId,
                        expectedVersion,
                        result.status(),
                        result.drafts(),
                        result.questions(),
                        answers)
                .orElseThrow(RecipeImportSupport::conflict);
        return views.from(updated);
    }

    private List<RecipeImportAnswer> mergeAnswers(
            RecipeImportSession session,
            List<RecipeImportAnswer> submittedAnswers) {
        if (submittedAnswers == null || submittedAnswers.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Answer at least one follow-up question.");
        }
        Set<String> allowed = session.questions().stream()
                .map(question -> question.questionId())
                .collect(Collectors.toSet());
        LinkedHashMap<String, RecipeImportAnswer> merged = session.answers().stream()
                .collect(Collectors.toMap(
                        RecipeImportAnswer::questionId,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new));
        HashSet<String> submittedIds = new HashSet<>();
        for (RecipeImportAnswer answer : submittedAnswers) {
            if (answer == null || !allowed.contains(answer.questionId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more answers do not match the current questions.");
            }
            if (!submittedIds.add(answer.questionId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Duplicate question answers are not allowed.");
            }
            String value = RecipeImportLanguagePolicy.validateAnswer(answer.value());
            merged.put(answer.questionId(), new RecipeImportAnswer(answer.questionId(), value));
        }
        return List.copyOf(merged.values());
    }
}
