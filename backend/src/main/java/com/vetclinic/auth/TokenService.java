package com.vetclinic.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final JwtEncoder encoder;
	private final Clock clock;
	private final Duration ttl;

	public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.ttl}") Duration ttl) {
		this.encoder = encoder;
		this.clock = clock;
		this.ttl = ttl;
	}

	public String issue(UserAccount account) {
		return issue(account, Instant.now(clock).plus(ttl));
	}

	public Instant expiryFromNow() {
		return Instant.now(clock).plus(ttl);
	}

	public String issue(UserAccount account, Instant expiresAt) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.subject(String.valueOf(account.getId()))
			.issuedAt(Instant.now(clock))
			.expiresAt(expiresAt)
			.claim("role", account.getRole().name());
		if (account.getOwnerId() != null) {
			claims.claim("ownerId", account.getOwnerId());
		}
		if (account.getVeterinarianId() != null) {
			claims.claim("veterinarianId", account.getVeterinarianId());
		}
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
			.getTokenValue();
	}
}
