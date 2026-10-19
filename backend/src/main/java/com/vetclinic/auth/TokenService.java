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

/** Issues the stateless HS256 JWT that carries the user's identity and role. */
@Service
public class TokenService {

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.ttl}") Duration ttl) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public IssuedToken issue(UserAccount account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(account.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", account.getRole().name());
        if (account.getOwnerId() != null) {
            claims.claim("ownerId", account.getOwnerId());
        }
        if (account.getVeterinarianId() != null) {
            claims.claim("veterinarianId", account.getVeterinarianId());
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
