package com.foodmind.foodmindbackend.chat.api;

import com.foodmind.foodmindbackend.chat.api.request.ShareChatReferenceRequest;
import com.foodmind.foodmindbackend.chat.api.response.ChatReferenceResponse;
import com.foodmind.foodmindbackend.chat.application.ChatReferenceService;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@RestController
@RequestMapping("/api/v1/chat/sessions/{sessionId}/references")
public class ChatReferenceController {

    private final ChatReferenceService chatReferenceService;

    public ChatReferenceController(ChatReferenceService chatReferenceService) {
        this.chatReferenceService = chatReferenceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatReferenceResponse share(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ShareChatReferenceRequest request) {
        return ChatReferenceResponse.from(chatReferenceService.share(principal.id(), sessionId, request.toSource()));
    }
}
