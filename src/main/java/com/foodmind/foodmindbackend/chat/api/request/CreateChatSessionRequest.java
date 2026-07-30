package com.foodmind.foodmindbackend.chat.api.request;

import jakarta.validation.constraints.Size;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record CreateChatSessionRequest(@Size(max = 160) String title) {
}
