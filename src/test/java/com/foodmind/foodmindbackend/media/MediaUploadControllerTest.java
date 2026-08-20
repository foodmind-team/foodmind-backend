package com.foodmind.foodmindbackend.media;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description: Contract tests for bounded PENDING media upload instructions.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:40 pm
 */

@SpringBootTest(properties = {
        "foodmind.media.storage.enabled=true",
        "foodmind.media.storage.bucket=test-media",
        "foodmind.media.storage.max-byte-size=128"
})
@AutoConfigureMockMvc
@Import(MediaUploadControllerTest.StorageTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MediaUploadControllerTest extends PostgreSqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("TRUNCATE TABLE food_record, drink_record, media_asset, auth_session, app_user CASCADE");
        StorageTestConfiguration.METADATA.set(null);
        StorageTestConfiguration.DELETE_CALLED.set(false);
    }

    @Test
    void createsServerOwnedPendingAssetAndRedactsItsObjectKey() throws Exception {
        String token = tokenFor("media-owner@example.test");
        MvcResult result = mockMvc.perform(post("/api/v1/media/uploads")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/jpeg","byteSize":128,
                                 "checksumSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.uploadUrl").value(containsString("https://storage.test/")))
                .andExpect(jsonPath("$.requiredHeaders.Content-Type").value("image/jpeg"))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andReturn();

        String assetId = read(result, "$.mediaAssetId");
        String objectKey = jdbcTemplate.queryForObject("SELECT object_key FROM media_asset WHERE id = ?", String.class,
                java.util.UUID.fromString(assetId));
        org.assertj.core.api.Assertions.assertThat(objectKey).matches("media/[0-9a-f-]{36}/" + assetId + "/original");
    }

    @Test
    void rejectsUnsupportedSizeAndChecksumBeforeStorageAccess() throws Exception {
        String token = tokenFor("media-invalid@example.test");
        mockMvc.perform(post("/api/v1/media/uploads").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"byteSize\":0,\"checksumSha256\":\"not-a-checksum\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
        mockMvc.perform(post("/api/v1/media/uploads").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"byteSize\":129,\"checksumSha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MEDIA_SIZE_OUT_OF_RANGE"));
    }

    @Test
    void finalisesMatchingMetadataIdempotentlyAndHidesForeignAssets() throws Exception {
        String ownerToken = tokenFor("media-finalise-owner@example.test");
        String otherToken = tokenFor("media-finalise-other@example.test");
        String assetId = createAsset(ownerToken);
        StorageTestConfiguration.METADATA.set(new ObjectStoragePort.ObjectMetadata("image/jpeg", 128,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        mockMvc.perform(post("/api/v1/media/{id}/finalise", assetId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(post("/api/v1/media/{id}/finalise", assetId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(post("/api/v1/media/{id}/finalise", assetId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingOrMismatchedStorageObjectsAndDeletesThemSafely() throws Exception {
        String token = tokenFor("media-mismatch@example.test");
        String missingAssetId = createAsset(token);
        mockMvc.perform(post("/api/v1/media/{id}/finalise", missingAssetId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("OBJECT_NOT_FOUND"));

        String mismatchedAssetId = createAsset(token);
        StorageTestConfiguration.METADATA.set(new ObjectStoragePort.ObjectMetadata("image/png", 128,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        mockMvc.perform(post("/api/v1/media/{id}/finalise", mismatchedAssetId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("STORAGE_METADATA_MISMATCH"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM media_asset WHERE id = ?", String.class, java.util.UUID.fromString(mismatchedAssetId)))
                .isEqualTo("DELETED");
        org.assertj.core.api.Assertions.assertThat(StorageTestConfiguration.DELETE_CALLED.get()).isTrue();
    }

    @Test
    void deletionIsIdempotentAndPreventsFutureFinalisation() throws Exception {
        String token = tokenFor("media-delete@example.test");
        String assetId = createAsset(token);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/media/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/media/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/media/{id}/finalise", assetId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletionDetachesTheAssetFromItsRecord() throws Exception {
        String token = tokenFor("media-attached-delete@example.test");
        String assetId = createAsset(token);
        StorageTestConfiguration.METADATA.set(new ObjectStoragePort.ObjectMetadata("image/jpeg", 128,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        mockMvc.perform(post("/api/v1/media/{id}/finalise", assetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        String recordId = read(mockMvc.perform(post("/api/v1/food-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealNameSnapshot":"Attached meal","occurredAt":"2026-08-20T12:00:00Z",
                                 "visibility":"PRIVATE","mediaAssetId":"%s"}
                                """.formatted(assetId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaAssetId").value(assetId))
                .andReturn(), "$.id");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/media/{id}", assetId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/food-records/{id}", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssetId").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));
    }

    private String createAsset(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/media/uploads").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"byteSize\":128,\"checksumSha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.mediaAssetId");
    }

    private String tokenFor(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Media Owner","password":"correct horse battery",
                                 "clientType":"WEB","deviceLabel":"JUnit"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.accessToken");
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String token) { return "Bearer " + token; }

    @TestConfiguration
    static class StorageTestConfiguration {
        static final AtomicReference<ObjectStoragePort.ObjectMetadata> METADATA = new AtomicReference<>();
        static final AtomicBoolean DELETE_CALLED = new AtomicBoolean();

        @Bean
        @Primary
        ObjectStoragePort objectStoragePort() {
            return new ObjectStoragePort() {
                @Override
                public UploadInstruction createUploadInstruction(String key, String contentType, long byteSize, String checksum) {
                    return new UploadInstruction("https://storage.test/" + key,
                            Map.of("Content-Type", contentType, "Content-Length", Long.toString(byteSize)),
                            OffsetDateTime.parse("2026-07-30T16:45:00Z"));
                }
                @Override public ObjectMetadata headObject(String objectKey) {
                    ObjectStoragePort.ObjectMetadata metadata = METADATA.get();
                    if (metadata == null) throw new ObjectMissingException();
                    return metadata;
                }
                @Override public void deleteObject(String objectKey) { DELETE_CALLED.set(true); }
            };
        }
    }
}
