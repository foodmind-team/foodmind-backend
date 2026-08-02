package com.foodmind.foodmindbackend.recipe.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserRecipe {
    private final UserRecipeRepository repository;
    private final Clock clock;

    public CreateUserRecipe(UserRecipeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public UserRecipe handle(UUID ownerUserId, Command command) {
        validate(command);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return repository.create(new UserRecipe(UUID.randomUUID(), ownerUserId, command.name().trim(), command.servings(),
                trimToNull(command.imageUrl()), clean(command.tags()), clean(command.allergenHints()),
                clean(command.ingredients()), clean(command.steps()), now, now, 0));
    }

    static void validate(Command command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Recipe name is required.");
        }
        if (command.name().trim().length() > 160 || command.servings() < 1 || command.servings() > 50) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Recipe name or servings is invalid.");
        }
        if (command.ingredients() == null || command.ingredients().stream().noneMatch(value -> value != null && !value.isBlank())
                || command.steps() == null || command.steps().stream().noneMatch(value -> value != null && !value.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "At least one ingredient and step are required.");
        }
    }

    static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record Command(String name, int servings, String imageUrl, List<String> tags, List<String> allergenHints,
                          List<String> ingredients, List<String> steps) {}
}
