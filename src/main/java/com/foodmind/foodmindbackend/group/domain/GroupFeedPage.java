package com.foodmind.foodmindbackend.group.domain;

import java.util.List;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/07/2026 11:55 pm
 */

public record GroupFeedPage(List<GroupFeedEvent> items, String nextCursor) {
}
