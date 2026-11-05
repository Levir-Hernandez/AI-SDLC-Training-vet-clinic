package com.vetclinic.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.vetclinic.appointment.Actor;

public record CurrentUser(Long userId, Role role, Long ownerId, Long veterinarianId) {

	public Actor actor() {
		return Actor.from(role);
	}

	public static CurrentUser from(Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			throw new IllegalStateException("Authentication is not a JWT");
		}
		var claims = jwt.getToken().getClaims();
		return new CurrentUser(Long.valueOf(jwt.getToken().getSubject()), Role.valueOf((String) claims.get("role")),
				number(claims.get("ownerId")), number(claims.get("veterinarianId")));
	}

	private static Long number(Object value) {
		return value instanceof Number n ? n.longValue() : null;
	}
}
