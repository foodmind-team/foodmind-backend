package com.foodmind.foodmindbackend.recipe.application;

import com.foodmind.foodmindbackend.recipe.application.port.UserRecipeRepository;
import com.foodmind.foodmindbackend.recipe.domain.UserRecipePage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListUserRecipes {
    private final UserRecipeRepository repository;
    public ListUserRecipes(UserRecipeRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public UserRecipePage handle(UUID ownerUserId, int page, int size) { return repository.findOwnedPage(ownerUserId, page, size); }
}
