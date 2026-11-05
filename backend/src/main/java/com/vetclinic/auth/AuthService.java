package com.vetclinic.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.auth.dto.CurrentUserResponse;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.LoginResponse;
import com.vetclinic.shared.error.DomainException;

@Service
public class AuthService {

	private final UserAccountRepository accounts;
	private final PasswordEncoder encoder;
	private final TokenService tokens;

	public AuthService(UserAccountRepository accounts, PasswordEncoder encoder, TokenService tokens) {
		this.accounts = accounts;
		this.encoder = encoder;
		this.tokens = tokens;
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest req) {
		UserAccount account = accounts.findByUsername(req.username())
			.filter(a -> encoder.matches(req.password(), a.getPasswordHash()))
			.orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
					"Invalid username or password"));
		var expiresAt = tokens.expiryFromNow();
		return new LoginResponse(tokens.issue(account, expiresAt), expiresAt, account.getRole(),
				account.getDisplayName());
	}

	@Transactional(readOnly = true)
	public CurrentUserResponse me(CurrentUser user) {
		UserAccount a = accounts.findById(user.userId())
			.orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Account no longer exists"));
		return new CurrentUserResponse(a.getId(), a.getUsername(), a.getRole(), a.getDisplayName(), a.getOwnerId(),
				a.getVeterinarianId());
	}
}
