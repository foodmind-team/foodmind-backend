package com.foodmind.foodmindbackend.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.recipe.application.CreateUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.DeleteUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.UpdateUserRecipe;
import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipe;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipePage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserRecipeApplicationTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID RECIPE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsOnlyValidatedOwnerRecipe() {
        FakeRepository repository = new FakeRepository();
        UserRecipe created = new CreateUserRecipe(repository, CLOCK).handle(OWNER, command());
        assertEquals(OWNER, created.ownerUserId());
        assertEquals("番茄意面", created.name());
        assertEquals(List.of("番茄", "意面"), created.ingredients());
    }

    @Test
    void rejectsRecipeWithoutIngredientOrStep() {
        CreateUserRecipe.Command invalid = new CreateUserRecipe.Command("缺步骤", 2, null, List.of(), List.of(), List.of(), List.of());
        assertThrows(ApiException.class, () -> new CreateUserRecipe(new FakeRepository(), CLOCK).handle(OWNER, invalid));
    }

    @Test
    void updateUsesOptimisticVersionAndDeleteIsOwnerScoped() {
        FakeRepository repository = new FakeRepository();
        repository.recipe = recipe(OWNER, 2);
        UserRecipe updated = new UpdateUserRecipe(repository, CLOCK).handle(OWNER, RECIPE_ID, 2, command());
        assertEquals(3, updated.version());
        assertTrue(repository.updated);
        new DeleteUserRecipe(repository).handle(OWNER, RECIPE_ID);
        assertEquals(OWNER, repository.deletedOwner);
    }

    private static CreateUserRecipe.Command command() {
        return new CreateUserRecipe.Command("番茄意面", 2, null, List.of("快手"), List.of(), List.of("番茄", "意面"), List.of("煮面", "拌匀"));
    }

    private static UserRecipe recipe(UUID owner, long version) {
        return new UserRecipe(RECIPE_ID, owner, "旧菜谱", 2, null, List.of(), List.of(), List.of("面"), List.of("煮"),
                Instant.parse("2026-08-01T00:00:00Z").atOffset(ZoneOffset.UTC), Instant.parse("2026-08-01T00:00:00Z").atOffset(ZoneOffset.UTC), version);
    }

    private static final class FakeRepository implements UserRecipeRepository {
        UserRecipe recipe;
        boolean updated;
        UUID deletedOwner;

        @Override public UserRecipe create(UserRecipe value) { recipe = value; return value; }
        @Override public Optional<UserRecipe> findOwned(UUID owner, UUID id) { return recipe != null && recipe.ownerUserId().equals(owner) ? Optional.of(recipe) : Optional.empty(); }
        @Override public UserRecipePage findOwnedPage(UUID owner, int page, int size) { return new UserRecipePage(List.of(), 0); }
        @Override public Optional<UserRecipe> update(UserRecipe value, long expectedVersion) {
            if (recipe == null || recipe.version() != expectedVersion) return Optional.empty();
            updated = true; recipe = value; return Optional.of(value);
        }
        @Override public boolean deleteOwned(UUID owner, UUID id) { deletedOwner = owner; return recipe != null && recipe.ownerUserId().equals(owner); }
    }
}
