package com.vetclinic.auth;

import com.vetclinic.appointment.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Caller identity passed to use cases so role and ownership checks live in services. */
public record CurrentUser(Long userId, Role role, Long ownerId, Long veterinarianId) {

    public Actor actor() {
        return Actor.from(role);
    }

    /** Builds the caller from the validated JWT claims (sub, role, ownerId, veterinarianId). */
    public static CurrentUser from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Request is not authenticated with a JWT");
        }
        Jwt jwt = token.getToken();
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")),
                longClaim(jwt, "ownerId"), longClaim(jwt, "veterinarianId"));
    }

    private static Long longClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof Number n ? n.longValue() : null;
    }
}
