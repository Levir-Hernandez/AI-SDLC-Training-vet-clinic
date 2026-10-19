package com.vetclinic.auth.dto;

import com.vetclinic.auth.Role;
import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, Role role, String displayName) {
}
