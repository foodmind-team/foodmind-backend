package com.foodmind.foodmindbackend.wanttotry.application;

import com.foodmind.foodmindbackend.wanttotry.application.port.WantToTryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Service
public class DeleteWantToTry {

    private final WantToTryRepository repository;

    public DeleteWantToTry(WantToTryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void handle(UUID ownerUserId, UUID id) {
        repository.softDeleteOwned(ownerUserId, id);
    }
}
