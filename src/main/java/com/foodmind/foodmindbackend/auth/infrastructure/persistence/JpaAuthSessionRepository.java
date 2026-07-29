package com.foodmind.foodmindbackend.auth.infrastructure.persistence;

import com.foodmind.foodmindbackend.auth.application.port.AuthSessionRepository;
import com.foodmind.foodmindbackend.auth.domain.AuthSession;
import com.foodmind.foodmindbackend.auth.infrastructure.persistence.entity.AuthSessionEntity;
import com.foodmind.foodmindbackend.auth.infrastructure.persistence.mapper.AuthSessionMapper;
import com.foodmind.foodmindbackend.auth.infrastructure.persistence.repository.AuthSessionJpaRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Repository
public class JpaAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionJpaRepository repository;
    private final AuthSessionMapper mapper;

    public JpaAuthSessionRepository(AuthSessionJpaRepository repository, AuthSessionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AuthSession save(AuthSession session) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(session)));
    }

    @Override
    public Optional<AuthSession> findByRefreshTokenHashForUpdate(String refreshTokenHash) {
        return repository.findByRefreshTokenHash(refreshTokenHash).map(mapper::toDomain);
    }

    @Override
    public void rotate(AuthSession predecessor, AuthSession successor, OffsetDateTime rotatedAt) {
        repository.saveAndFlush(mapper.toEntity(successor));
        AuthSessionEntity predecessorEntity = repository.getReferenceById(predecessor.id());
        predecessorEntity.setRotatedAt(rotatedAt);
        predecessorEntity.setReplacedBySessionId(successor.id());
        repository.flush();
    }

    @Override
    public void revoke(UUID sessionId, OffsetDateTime revokedAt) {
        repository.revoke(sessionId, revokedAt);
    }

    @Override
    public void revokeActiveForUser(UUID userId, OffsetDateTime revokedAt) {
        repository.revokeActiveForUser(userId, revokedAt);
    }

    @Override
    public void revokeFamily(UUID tokenFamilyId, OffsetDateTime revokedAt) {
        repository.revokeFamily(tokenFamilyId, revokedAt);
    }

    @Override
    public int deleteExpiredOrRevokedBefore(OffsetDateTime cutoff) {
        return repository.deleteExpiredOrRevokedBefore(cutoff);
    }
}
