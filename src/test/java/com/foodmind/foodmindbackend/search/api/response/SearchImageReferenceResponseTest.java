package com.foodmind.foodmindbackend.search.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodmind.foodmindbackend.search.domain.ExplorePage;
import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchImageReferenceResponseTest {

    private static final UUID CURATED_PLACE_ID = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final String CURATED_IMAGE = "/api/v1/catalogue-images/" + CURATED_PLACE_ID;

    @Test
    void searchAndExploreReturnTheSameBackendImageReferenceForCuratedContent() {
        SearchDocument document = new SearchDocument(
                SearchSourceType.PLACE, CURATED_PLACE_ID, null, null, "CURATED", "Orchard Garden Kitchen",
                "Orchard", null, null, null, null, null);

        SearchPageResponse search = SearchPageResponse.from(new SearchPage(List.of(document), null), key -> {
            throw new AssertionError("A curated image must not invoke the private-media signer.");
        });
        ExplorePageResponse explore = ExplorePageResponse.from(new ExplorePage(List.of(document), null), key -> {
            throw new AssertionError("A curated image must not invoke the private-media signer.");
        });

        assertThat(search.items().get(0).imageReference()).isEqualTo(CURATED_IMAGE);
        assertThat(explore.items().get(0).imageReference()).isEqualTo(CURATED_IMAGE);
    }
}
