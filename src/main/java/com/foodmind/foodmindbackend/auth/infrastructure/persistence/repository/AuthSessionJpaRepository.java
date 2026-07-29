package com.foodmind.foodmindbackend.auth.infrastructure.persistence.repository;

import com.foodmind.foodmindbackend.auth.infrastructure.persistence.entity.AuthSessionEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.id = :sessionId
               and session.revokedAt is null
            """)
    void revoke(@Param("sessionId") UUID sessionId, @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.userId = :userId
               and session.revokedAt is null
               and session.rotatedAt is null
               and session.expiresAt > :revokedAt
            """)
    void revokeActiveForUser(@Param("userId") UUID userId, @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.tokenFamilyId = :tokenFamilyId
               and session.revokedAt is null
            """)
    void revokeFamily(@Param("tokenFamilyId") UUID tokenFamilyId, @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("""
            delete from AuthSessionEntity session
             where session.expiresAt < :cutoff
               and session.revokedAt is not null
            """)
    int deleteExpiredOrRevokedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
