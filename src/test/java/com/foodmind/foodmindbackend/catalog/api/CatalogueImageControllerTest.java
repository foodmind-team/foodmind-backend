package com.foodmind.foodmindbackend.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogueImageControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CatalogueImageController()).build();

    @Test
    void servesTheKnownCuratedImageWithNoCache() throws Exception {
        mockMvc.perform(get("/api/v1/catalogue-images/{sourceId}", "21000000-0000-4000-8000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void servesEveryRegisteredCatalogueImage() throws Exception {
        for (String sourceId : List.of(
                "fb61151e-a29a-507b-91b5-09907116fc35",
                "ff90c8dc-7fe3-50c6-aaf0-8ea10f73c782",
                "a032d001-48a7-517a-bef0-95bc39640bca",
                "5801e48d-a6bb-5ab5-b3e7-93791ea05ada",
                "5ad2685a-0bad-528f-a067-54c3916fd7ac",
                "92ec5b73-2358-548d-bbff-c8d5e4c49993",
                "21000000-0000-4000-8000-000000000001",
                "21000000-0000-4000-8000-000000000002",
                "21000000-0000-4000-8000-000000000003",
                "21000000-0000-4000-8000-000000000004",
                "23000000-0000-4000-8000-000000000001",
                "23000000-0000-4000-8000-000000000002",
                "23000000-0000-4000-8000-000000000003")) {
            mockMvc.perform(get("/api/v1/catalogue-images/{sourceId}", sourceId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
        }
    }

    @Test
    void doesNotExposeUnmappedResources() throws Exception {
        mockMvc.perform(get("/api/v1/catalogue-images/{sourceId}", "00000000-0000-4000-8000-000000000099"))
                .andExpect(status().isNotFound());
    }
}
