package com.foodmind.foodmindbackend.wanttotry.application;

import com.foodmind.foodmindbackend.wanttotry.application.port.WantToTryRepository;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryPage;
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
public class ListWantToTry {

    private final WantToTryRepository repository;

    public ListWantToTry(WantToTryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WantToTryPage handle(UUID ownerUserId, int page, int size) {
        return repository.findOwnerPage(ownerUserId, page, size);
    }
}
