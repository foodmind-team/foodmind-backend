package com.foodmind.foodmindbackend.chat.api;

import com.foodmind.foodmindbackend.chat.api.request.CreateChatSessionRequest;
import com.foodmind.foodmindbackend.chat.api.response.ChatPageResponse;
import com.foodmind.foodmindbackend.chat.api.response.ChatSessionResponse;
import com.foodmind.foodmindbackend.chat.application.ChatSessionService;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

@Validated
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody CreateChatSessionRequest request) {
        return ChatSessionResponse.from(chatSessionService.create(principal.id(), request.title()));
    }

    @GetMapping
    public ChatPageResponse<ChatSessionResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ChatPageResponse.from(chatSessionService.list(principal.id(), page, size), ChatSessionResponse::from);
    }

    @GetMapping("/{sessionId}")
    public ChatSessionResponse get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId) {
        return ChatSessionResponse.from(chatSessionService.get(principal.id(), sessionId));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID sessionId) {
        chatSessionService.archive(principal.id(), sessionId);
    }
}
