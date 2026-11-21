package com.payledger.config;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {
}
