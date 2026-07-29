package com.foodmind.foodmindbackend.wanttotry.application;

import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.wanttotry.application.port.WantToTryRepository;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryItem;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySource;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTrySourceType;
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
public class SaveWantToTry {

    private static final int MAX_NOTE_LENGTH = 2000;

    private final WantToTryRepository repository;

    public SaveWantToTry(WantToTryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WantToTryItem handle(UUID ownerUserId, Command command) {
        if (command.sourceType() == null || command.sourceId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A sourceType and sourceId are required.");
        }
        String note = trimToNull(command.note());
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Want to Try note must be 2000 characters or fewer.");
        }
        WantToTrySource source = new WantToTrySource(command.sourceType(), command.sourceId());
        if (repository.resolveSource(ownerUserId, source).isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return repository.insertOrResolveDuplicate(ownerUserId, source, note);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record Command(WantToTrySourceType sourceType, UUID sourceId, String note) {
    }
}
