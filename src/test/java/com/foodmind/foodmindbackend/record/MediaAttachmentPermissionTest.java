package com.foodmind.foodmindbackend.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.record.application.port.DrinkRecordQuery;
import com.foodmind.foodmindbackend.record.application.port.FoodRecordQuery;
import com.foodmind.foodmindbackend.support.PostgreSqlContainerSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * @description: Enforces that a media asset is owned, READY, active, and unattached.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:55 pm
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MediaAttachmentPermissionTest extends PostgreSqlContainerSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FoodRecordQuery foodRecords;
    @Autowired private DrinkRecordQuery drinkRecords;

    @BeforeEach
    void cleanUserContent() {
        jdbcTemplate.execute("TRUNCATE TABLE food_record, drink_record, media_asset, auth_session, app_user CASCADE");
    }

    @Test
    void onlyOwnedReadyAndUnattachedAssetsCanBeAttached() throws Exception {
        UUID ownerId = UUID.fromString(read(register("media-record-owner@example.test"), "$.userId"));
        UUID otherId = UUID.fromString(read(register("media-record-other@example.test"), "$.userId"));
        UUID assetId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO media_asset (id, owner_user_id, object_key, content_type, byte_size, checksum_sha256, status, finalised_at)
                VALUES (?, ?, ?, 'image/jpeg', 128, repeat('a', 64), 'READY', CURRENT_TIMESTAMP)
                """, assetId, ownerId, "media/" + ownerId + "/" + assetId + "/original");

        assertThat(foodRecords.readyMediaExistsForOwner(ownerId, assetId)).isTrue();
        assertThat(drinkRecords.readyMediaExistsForOwner(ownerId, assetId)).isTrue();
        assertThat(foodRecords.readyMediaExistsForOwner(otherId, assetId)).isFalse();

        jdbcTemplate.update("""
                INSERT INTO food_record (id, owner_user_id, meal_name_snapshot, occurred_at, media_asset_id)
                VALUES (?, ?, 'Attached asset', CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), ownerId, assetId);
        assertThat(foodRecords.readyMediaExistsForOwner(ownerId, assetId)).isFalse();
        assertThat(drinkRecords.readyMediaExistsForOwner(ownerId, assetId)).isFalse();

        jdbcTemplate.update("UPDATE media_asset SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP WHERE id = ?", assetId);
        assertThat(foodRecords.readyMediaExistsForOwner(ownerId, assetId)).isFalse();
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Media Record","password":"correct horse battery",
                                 "clientType":"WEB","deviceLabel":"JUnit"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
