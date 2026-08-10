package com.foodmind.foodmindbackend.recipe.importing.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportAgentPort;
import com.foodmind.foodmindbackend.recipe.importing.application.port.RecipeImportRepository;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportAnswer;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportDraft;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportSession;
import com.foodmind.foodmindbackend.recipe.importing.domain.RecipeImportStatus;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        RecipeImportAgentPort.Result result = agent.parse(
                importId.toString(),
                session.sourceText(),
                answers,
                session.drafts(),
                session.questions());
        RecipeImportAgentResultValidator.validate(result);
        List<RecipeImportDraft> stableDrafts = preserveResolvedFields(session, result.drafts());
        RecipeImportSession updated = repository.updateAgentResult(
                        ownerUserId,
                        importId,
                        expectedVersion,
                        result.status(),
                        stableDrafts,
                        result.questions(),
                        answers)
                .orElseThrow(RecipeImportSupport::conflict);
        return views.from(updated);
    }

    /**
     * Clarification re-runs the stateless Agent so it can translate free-text answers. Fields that were
     * already resolved must remain byte-for-byte stable; otherwise model variance can regress a previously
     * English draft or silently change quantities while the user is only answering a servings question.
     */
    private List<RecipeImportDraft> preserveResolvedFields(
            RecipeImportSession session,
            List<RecipeImportDraft> reparsedDrafts) {
        Map<String, RecipeImportDraft> previousById = session.drafts().stream()
                .collect(Collectors.toMap(RecipeImportDraft::draftId, Function.identity()));
        Set<String> unresolvedFields = session.questions().stream()
                .map(question -> question.draftId() + ":" + question.fieldPath())
                .collect(Collectors.toSet());
        return reparsedDrafts.stream().map(reparsed -> {
            RecipeImportDraft previous = previousById.get(reparsed.draftId());
            if (previous == null) {
                return reparsed;
            }
            String prefix = reparsed.draftId() + ":";
            return new RecipeImportDraft(
                    reparsed.draftId(),
                    unresolvedFields.contains(prefix + "name") ? reparsed.name() : previous.name(),
                    unresolvedFields.contains(prefix + "servings") ? reparsed.servings() : previous.servings(),
                    unresolvedFields.contains(prefix + "ingredients") ? reparsed.ingredients() : previous.ingredients(),
                    unresolvedFields.contains(prefix + "steps") ? reparsed.steps() : previous.steps());
        }).toList();
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
            String value = RecipeImportInputPolicy.validateAnswer(answer.value());
            merged.put(answer.questionId(), new RecipeImportAnswer(answer.questionId(), value));
        }
        return List.copyOf(merged.values());
    }
}
