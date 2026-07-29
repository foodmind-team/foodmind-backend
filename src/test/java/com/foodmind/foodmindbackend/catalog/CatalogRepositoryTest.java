package com.foodmind.foodmindbackend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.catalog.application.port.CatalogueCandidateQuery;
import com.foodmind.foodmindbackend.catalog.application.port.CatalogueDetailQuery;
import com.foodmind.foodmindbackend.catalog.application.port.CatalogueReferenceDataQuery;
import com.foodmind.foodmindbackend.catalog.domain.OfferingCandidate;
import com.foodmind.foodmindbackend.catalog.domain.RecipeCandidate;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 9:36 pm
 */

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogRepositoryTest extends PostgreSqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogueReferenceDataQuery referenceDataQuery;

    @Autowired
    private CatalogueDetailQuery detailQuery;

    @Autowired
    private CatalogueCandidateQuery candidateQuery;

    @Test
    void v11SeedCountsMatchManifestAndUseSyntheticProvenance() throws IOException {
        Properties manifest = new Properties();
        manifest.load(new ClassPathResource("fixtures/catalogue/v11-seed-manifest.properties").getInputStream());

        for (String tableName : List.of(
                "cuisine",
                "dietary_tag",
                "allergen",
                "meal",
                "meal_dietary_tag",
                "meal_allergen",
                "place",
                "place_meal",
                "place_observation",
                "food_product",
                "food_product_dietary_tag",
                "food_product_allergen",
                "recipe",
                "ingredient",
                "recipe_ingredient",
                "recipe_step",
                "recipe_dietary_tag",
                "recipe_allergen")) {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
            assertThat(count).isEqualTo(Integer.valueOf(manifest.getProperty(tableName)));
        }

        Integer observations = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM place_observation
                WHERE observation_type = 'CLEANLINESS'
                  AND source_kind = 'CURATED_DEMO'
                  AND observed_at = '2026-07-21T04:00:00Z'
                  AND note LIKE '%not an inspection or safety certification%'
                """, Integer.class);
        assertThat(observations).isEqualTo(4);
    }

    @Test
    void activeOfferingCandidatesReferenceActiveParentsAndCarryPriceContext() {
        List<OfferingCandidate> candidates = candidateQuery.activeOfferingCandidates(50);

        assertThat(candidates).hasSize(10);
        assertThat(candidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.offeringId()).isNotNull();
                    assertThat(candidate.mealId()).isNotNull();
                    assertThat(candidate.placeId()).isNotNull();
                    assertThat(candidate.price().currency()).isEqualTo("SGD");
                    assertThat(candidate.price().amount()).isPositive();
                });
        assertThat(candidates)
                .extracting(OfferingCandidate::area)
                .contains("Orchard", "Tiong Bahru", "Serangoon", "Tampines");

        Integer activeParentCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM place_meal pm
                JOIN meal m ON m.id = pm.meal_id
                JOIN place p ON p.id = pm.place_id
                WHERE pm.available
                  AND m.curation_status = 'ACTIVE'
                  AND p.curation_status = 'ACTIVE'
                """, Integer.class);
        assertThat(activeParentCount).isEqualTo(candidates.size());
    }

    @Test
    void stableCodeLookupsAndDetailsResolveOnlyActiveCatalogueRows() {
        assertThat(referenceDataQuery.findCuisineByCode(" singaporean "))
                .hasValueSatisfying(cuisine -> assertThat(cuisine.name()).isEqualTo("Singaporean"));
        assertThat(referenceDataQuery.findAllergenByCode("peanut"))
                .hasValueSatisfying(allergen -> assertThat(allergen.id()).isEqualTo(UUID.fromString("12000000-0000-4000-8000-000000000001")));

        UUID seedMealId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID seedPlaceId = UUID.fromString("21000000-0000-4000-8000-000000000001");
        UUID seedProductId = UUID.fromString("23000000-0000-4000-8000-000000000001");

        assertThat(detailQuery.findActiveMeal(seedMealId))
                .hasValueSatisfying(meal -> {
                    assertThat(meal.cuisine().code()).isEqualTo("SINGAPOREAN");
                    assertThat(meal.offerings()).hasSize(2);
                    assertThat(meal.allergenCodes()).containsExactly("GLUTEN", "SOY");
                });
        assertThat(detailQuery.findActivePlace(seedPlaceId))
                .hasValueSatisfying(place -> {
                    assertThat(place.observations()).hasSize(1);
                    assertThat(place.offerings()).hasSize(3);
                });
        assertThat(detailQuery.findActiveProduct(seedProductId))
                .hasValueSatisfying(product -> {
                    assertThat(product.place()).isNull();
                    assertThat(product.allergenCodes()).containsExactly("SOY");
                });
    }

    @Test
    void recipeCandidatesPreserveIngredientAndStepOrder() {
        List<RecipeCandidate> recipes = candidateQuery.controlledRecipeCandidates(10);

        assertThat(recipes).hasSize(3);
        RecipeCandidate tofuBowl = recipes.stream()
                .filter(recipe -> recipe.name().equals("Ginger Tofu Rice Bowl"))
                .findFirst()
                .orElseThrow();

        assertThat(tofuBowl.dietaryTagCodes()).containsExactly("VEGAN", "VEGETARIAN");
        assertThat(tofuBowl.allergenCodes()).containsExactly("GLUTEN", "SESAME", "SOY");
        assertThat(tofuBowl.ingredients())
                .extracting(RecipeCandidate.IngredientLine::sequenceNo)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(tofuBowl.steps())
                .extracting(RecipeCandidate.StepLine::stepNo)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void databaseCatalogueConstraintsRejectDuplicateNaturalKeysAndBrokenCoordinates() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO place_meal (
                            id, place_id, meal_id, display_name, price, currency
                        )
                        VALUES (
                            '22ffffff-0000-4000-8000-000000000001',
                            '21000000-0000-4000-8000-000000000001',
                            '20000000-0000-4000-8000-000000000001',
                            'Garden Chicken Rice',
                            7.50,
                            'SGD'
                        )
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO place (
                            id, name, place_type, area, latitude, curation_status
                        )
                        VALUES (
                            '21ffffff-0000-4000-8000-000000000001',
                            'Broken Coordinate Demo',
                            'CAFE',
                            'Test Area',
                            1.300000,
                            'ACTIVE'
                        )
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
