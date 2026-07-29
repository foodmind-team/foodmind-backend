package com.foodmind.foodmindbackend.auth.infrastructure.persistence.mapper;

import com.foodmind.foodmindbackend.auth.domain.AuthSession;
import com.foodmind.foodmindbackend.auth.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Component
public class AuthSessionMapper {

    public AuthSession toDomain(AuthSessionEntity entity) {
        return new AuthSession(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenFamilyId(),
                entity.getRefreshTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRotatedAt(),
                entity.getRevokedAt(),
                entity.getReplacedBySessionId(),
                entity.getClientType(),
                entity.getDeviceLabel());
    }

    public AuthSessionEntity toEntity(AuthSession session) {
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setId(session.id());
        entity.setUserId(session.userId());
        entity.setTokenFamilyId(session.tokenFamilyId());
        entity.setRefreshTokenHash(session.refreshTokenHash());
        entity.setIssuedAt(session.issuedAt());
        entity.setExpiresAt(session.expiresAt());
        entity.setRotatedAt(session.rotatedAt());
        entity.setRevokedAt(session.revokedAt());
        entity.setReplacedBySessionId(session.replacedBySessionId());
        entity.setClientType(session.clientType());
        entity.setDeviceLabel(session.deviceLabel());
        return entity;
    }
}
