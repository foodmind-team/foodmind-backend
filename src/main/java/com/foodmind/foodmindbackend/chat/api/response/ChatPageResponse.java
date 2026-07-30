package com.foodmind.foodmindbackend.chat.api.response;

import com.foodmind.foodmindbackend.chat.domain.ChatPage;
import java.util.List;
import java.util.function.Function;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public record ChatPageResponse<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static <S, T> ChatPageResponse<T> from(ChatPage<S> page, Function<S, T> mapper) {
        return new ChatPageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.nextCursor(),
                page.hasNext());
    }
}
