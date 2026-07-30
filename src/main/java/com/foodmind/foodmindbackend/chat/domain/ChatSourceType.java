package com.foodmind.foodmindbackend.chat.domain;

import com.foodmind.foodmindbackend.search.domain.SearchSourceType;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 01:05 pm
 */

public enum ChatSourceType {
    FOOD_RECORD,
    FOOD_PRODUCT,
    PLACE;

    public SearchSourceType toSearchSourceType() {
        return SearchSourceType.valueOf(name());
    }
}
