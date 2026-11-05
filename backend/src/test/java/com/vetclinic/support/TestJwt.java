package com.vetclinic.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vetclinic.auth.Role;

/** Issues test tokens signed with the development secret from application.yml. */
public final class TestJwt {

	public static final String DEV_SECRET = "dev-only-change-me-dev-only-change-me-0123456789";

	private TestJwt() {
	}

	public static String token(Role role, Long userId, Long ownerId, Long veterinarianId) {
		return token(DEV_SECRET, role, userId, ownerId, veterinarianId);
	}

	public static String receptionist() {
		return token(Role.RECEPTIONIST, 1L, null, null);
	}

	public static String veterinarian(Long vetId) {
		return token(Role.VETERINARIAN, 2L, null, vetId);
	}

	public static String owner(Long ownerId) {
		return token(Role.OWNER, 3L, ownerId, null);
	}

	public static String token(String secret, Role role, Long userId, Long ownerId, Long veterinarianId) {
		var encoder = new NimbusJwtEncoder(
				new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.subject(String.valueOf(userId))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.claim("role", role.name());
		if (ownerId != null) {
			claims.claim("ownerId", ownerId);
		}
		if (veterinarianId != null) {
			claims.claim("veterinarianId", veterinarianId);
		}
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
			.getTokenValue();
	}
}
