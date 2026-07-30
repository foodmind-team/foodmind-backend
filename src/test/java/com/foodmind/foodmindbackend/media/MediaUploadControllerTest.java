package com.foodmind.foodmindbackend.media;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.media.application.port.ObjectStoragePort;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.Map;
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
                @Override public ObjectMetadata headObject(String objectKey) { throw new UnsupportedOperationException(); }
                @Override public void deleteObject(String objectKey) { }
            };
        }
    }
}
