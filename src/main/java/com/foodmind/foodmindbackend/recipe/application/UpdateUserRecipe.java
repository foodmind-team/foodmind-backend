package com.foodmind.foodmindbackend.recipe.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUserRecipe {
    private final UserRecipeRepository repository;
    private final Clock clock;
    public UpdateUserRecipe(UserRecipeRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    @Transactional
    public UserRecipe handle(UUID ownerUserId, UUID recipeId, long expectedVersion, CreateUserRecipe.Command command) {
        CreateUserRecipe.validate(command);
        UserRecipe current = repository.findOwned(ownerUserId, recipeId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Recipe was not found."));
        UserRecipe next = new UserRecipe(current.id(), ownerUserId, command.name().trim(), command.servings(), CreateUserRecipe.trimToNull(command.imageUrl()),
                CreateUserRecipe.clean(command.tags()), CreateUserRecipe.clean(command.allergenHints()), CreateUserRecipe.clean(command.ingredients()),
                CreateUserRecipe.clean(command.steps()), current.createdAt(), OffsetDateTime.now(clock), current.version() + 1);
        return repository.update(next, expectedVersion).orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "Recipe was changed; reload before saving."));
    }
}
