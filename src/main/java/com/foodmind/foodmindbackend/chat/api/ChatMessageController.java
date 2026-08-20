package com.foodmind.foodmindbackend.chat.api;

import com.foodmind.foodmindbackend.chat.api.request.PostChatMessageRequest;
import com.foodmind.foodmindbackend.chat.api.response.ChatMessageResponse;
import com.foodmind.foodmindbackend.chat.api.response.ChatPageResponse;
import com.foodmind.foodmindbackend.chat.application.ChatMessageService;
import com.foodmind.foodmindbackend.chat.domain.ChatCursor;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi-test
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/chat/sessions/{sessionId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse post(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostChatMessageRequest request) {
        return ChatMessageResponse.from(chatMessageService.post(
                principal.id(),
                sessionId,
                request.content(),
                request.referenceIds(),
                request.useSessionReferences(),
                idempotencyKey));
    }

    @GetMapping
    public ChatPageResponse<ChatMessageResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ChatMessageService.MAX_PAGE_SIZE) int size) {
        return ChatPageResponse.from(
                chatMessageService.list(principal.id(), sessionId, size, ChatCursor.after(after)),
                ChatMessageResponse::from);
    }
}
