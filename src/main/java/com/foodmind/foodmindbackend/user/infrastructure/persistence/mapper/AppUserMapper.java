package com.foodmind.foodmindbackend.user.infrastructure.persistence.mapper;

import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.infrastructure.persistence.entity.AppUserEntity;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

@Component
public class AppUserMapper {

    public User toDomain(AppUserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getNormalisedEmail(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getTimeZone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastLoginAt(),
                entity.getDeactivatedAt(),
                entity.getVersion());
    }

    public AppUserEntity toEntity(User user) {
        AppUserEntity entity = new AppUserEntity();
        entity.setId(user.id());
        entity.setEmail(user.email());
        entity.setNormalisedEmail(user.normalisedEmail());
        entity.setPasswordHash(user.passwordHash());
        entity.setDisplayName(user.displayName());
        entity.setRole(user.role());
        entity.setStatus(user.status());
        entity.setTimeZone(user.timeZone());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setDeactivatedAt(user.deactivatedAt());
        entity.setVersion(user.version());
        return entity;
    }
}
