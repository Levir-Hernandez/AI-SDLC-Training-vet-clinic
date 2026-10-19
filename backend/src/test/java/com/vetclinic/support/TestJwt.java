package com.vetclinic.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vetclinic.auth.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Issues test tokens with the same shape as the production ones. */
public final class TestJwt {

    /** Must match app.jwt.secret in src/test/resources/application.properties. */
    public static final String TEST_SECRET = "test-only-jwt-secret-0123456789-abcdefghijkl";

    private TestJwt() {
    }

    public static String receptionist() {
        return token(TEST_SECRET, 1L, Role.RECEPTIONIST, null, null, Instant.now().plusSeconds(3600));
    }

    public static String forRole(Role role) {
        return token(TEST_SECRET, 1L, role, role == Role.OWNER ? 10L : null,
                role == Role.VETERINARIAN ? 20L : null, Instant.now().plusSeconds(3600));
    }

    public static String signedWith(String secret) {
        return token(secret, 1L, Role.RECEPTIONIST, null, null, Instant.now().plusSeconds(3600));
    }

    public static String token(String secret, Long userId, Role role, Long ownerId, Long veterinarianId,
                               Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .claim("role", role.name());
        if (ownerId != null) {
            claims.claim("ownerId", ownerId);
        }
        if (veterinarianId != null) {
            claims.claim("veterinarianId", veterinarianId);
        }
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
