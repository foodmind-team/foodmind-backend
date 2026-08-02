package com.foodmind.foodmindbackend.media.infrastructure.persistence;

import com.foodmind.foodmindbackend.media.application.port.MediaAssetRepository;
import com.foodmind.foodmindbackend.media.domain.model.MediaAsset;
import com.foodmind.foodmindbackend.media.domain.model.MediaAssetStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description: JDBC implementation with owner-scoped lifecycle updates.
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 11:35 pm
 */

@Repository
public class JdbcMediaAssetRepository implements MediaAssetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMediaAssetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void savePending(MediaAsset asset) {
        jdbcTemplate.update("""
                        INSERT INTO media_asset (
                            id, owner_user_id, object_key, content_type, byte_size, checksum_sha256, status, created_at
                        )
                        VALUES (
                            :id, :ownerUserId, :objectKey, :contentType, :byteSize, :checksumSha256, 'PENDING', :createdAt
                        )
                        """, parameters(asset));
    }

    @Override
    public Optional<MediaAsset> findOwned(UUID ownerUserId, UUID assetId) {
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, object_key, content_type, byte_size, checksum_sha256,
                               status, created_at, finalised_at, deleted_at
                        FROM media_asset
                        WHERE id = :id AND owner_user_id = :ownerUserId
                        """,
                new MapSqlParameterSource().addValue("id", assetId).addValue("ownerUserId", ownerUserId),
                (rs, rowNum) -> mapAsset(rs))
                .stream().findFirst();
    }

    @Override
    public boolean markReady(UUID ownerUserId, UUID assetId, OffsetDateTime finalisedAt) {
        return jdbcTemplate.update("""
                        UPDATE media_asset
                        SET status = 'READY', finalised_at = :finalisedAt
                        WHERE id = :id AND owner_user_id = :ownerUserId AND status = 'PENDING'
                        """, new MapSqlParameterSource().addValue("id", assetId).addValue("ownerUserId", ownerUserId)
                .addValue("finalisedAt", finalisedAt)) == 1;
    }

    @Override
    public Optional<MediaAsset> softDelete(UUID ownerUserId, UUID assetId, OffsetDateTime deletedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE media_asset
                        SET status = 'DELETED', deleted_at = :deletedAt
                        WHERE id = :id AND owner_user_id = :ownerUserId AND status IN ('PENDING', 'READY')
                        """, new MapSqlParameterSource().addValue("id", assetId).addValue("ownerUserId", ownerUserId)
                .addValue("deletedAt", deletedAt));
        if (updated == 0) {
            return findOwned(ownerUserId, assetId);
        }
        return findOwned(ownerUserId, assetId);
    }

    @Override
    public List<MediaAsset> findPendingCreatedBefore(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, object_key, content_type, byte_size, checksum_sha256,
                               status, created_at, finalised_at, deleted_at
                        FROM media_asset WHERE status = 'PENDING' AND created_at < :cutoff
                        ORDER BY created_at ASC LIMIT :limit
                        """, new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", limit),
                (rs, rowNum) -> mapAsset(rs));
    }

    @Override
    public List<MediaAsset> findDeletedBefore(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, owner_user_id, object_key, content_type, byte_size, checksum_sha256,
                               status, created_at, finalised_at, deleted_at
                        FROM media_asset WHERE status = 'DELETED' AND deleted_at < :cutoff
                        ORDER BY deleted_at ASC LIMIT :limit
                        """, new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", limit),
                (rs, rowNum) -> mapAsset(rs));
    }

    private MapSqlParameterSource parameters(MediaAsset asset) {
        return new MapSqlParameterSource().addValue("id", asset.id()).addValue("ownerUserId", asset.ownerUserId())
                .addValue("objectKey", asset.objectKey()).addValue("contentType", asset.contentType())
                .addValue("byteSize", asset.byteSize()).addValue("checksumSha256", asset.checksumSha256())
                .addValue("createdAt", asset.createdAt());
    }

    private MediaAsset mapAsset(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MediaAsset(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class),
                rs.getString("object_key"), rs.getString("content_type"), rs.getLong("byte_size"),
                rs.getString("checksum_sha256"), MediaAssetStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("finalised_at", OffsetDateTime.class),
                rs.getObject("deleted_at", OffsetDateTime.class));
    }
}
