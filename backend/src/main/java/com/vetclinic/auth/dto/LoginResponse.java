package com.vetclinic.auth.dto;

import java.time.Instant;

import com.vetclinic.auth.Role;

public record LoginResponse(String token, Instant expiresAt, Role role, String displayName) {
}
