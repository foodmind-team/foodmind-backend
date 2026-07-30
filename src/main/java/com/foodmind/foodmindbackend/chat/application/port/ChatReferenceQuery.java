package com.foodmind.foodmindbackend.chat.application.port;

import com.foodmind.foodmindbackend.chat.domain.ChatReference;
import com.foodmind.foodmindbackend.chat.domain.ChatSourcePointer;
import com.foodmind.foodmindbackend.chat.domain.ChatSourceResolution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public interface ChatReferenceQuery {

    Optional<ChatSourceResolution> resolveAuthorised(UUID actorUserId, ChatSourcePointer source);

    List<ChatReference> resolveSessionReferences(UUID actorUserId, UUID sessionId, List<UUID> referenceIds);
}
