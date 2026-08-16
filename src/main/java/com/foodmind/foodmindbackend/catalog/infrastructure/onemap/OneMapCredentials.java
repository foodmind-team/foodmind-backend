package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

record OneMapCredentials(String email, String password) {
    OneMapCredentials {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("OneMap credentials are incomplete.");
        }
    }
}
