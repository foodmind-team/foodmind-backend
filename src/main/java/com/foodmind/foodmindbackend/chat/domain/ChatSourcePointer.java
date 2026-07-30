package com.foodmind.foodmindbackend.chat.domain;

import java.util.UUID;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatSourcePointer(ChatSourceType sourceType, UUID sourceId) {
}
