package com.foodmind.foodmindbackend.user.infrastructure.persistence;

import com.foodmind.foodmindbackend.user.application.port.UserAccountRepository;
import com.foodmind.foodmindbackend.user.domain.User;
import com.foodmind.foodmindbackend.user.infrastructure.persistence.entity.AppUserEntity;
import com.foodmind.foodmindbackend.user.infrastructure.persistence.mapper.AppUserMapper;
import com.foodmind.foodmindbackend.user.infrastructure.persistence.repository.AppUserJpaRepository;
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
public class JpaUserAccountRepository implements UserAccountRepository {

    private final AppUserJpaRepository repository;
    private final AppUserMapper mapper;

    public JpaUserAccountRepository(AppUserJpaRepository repository, AppUserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        AppUserEntity saved = repository.saveAndFlush(mapper.toEntity(user));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByNormalisedEmail(String normalisedEmail) {
        return repository.findByNormalisedEmail(normalisedEmail).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNormalisedEmail(String normalisedEmail) {
        return repository.existsByNormalisedEmail(normalisedEmail);
    }

    @Override
    public void updateLastLoginAt(UUID id, OffsetDateTime lastLoginAt) {
        AppUserEntity entity = repository.getReferenceById(id);
        entity.setLastLoginAt(lastLoginAt);
    }
}
