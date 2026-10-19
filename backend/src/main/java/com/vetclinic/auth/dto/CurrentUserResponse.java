package com.vetclinic.auth.dto;

import com.vetclinic.auth.Role;

public record CurrentUserResponse(Long userId, String username, Role role, String displayName) {
}
