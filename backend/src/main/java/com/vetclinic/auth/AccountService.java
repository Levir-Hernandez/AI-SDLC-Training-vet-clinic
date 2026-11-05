package com.vetclinic.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.shared.error.ConflictException;

@Service
public class AccountService {

	private final UserAccountRepository accounts;
	private final PasswordEncoder encoder;

	public AccountService(UserAccountRepository accounts, PasswordEncoder encoder) {
		this.accounts = accounts;
		this.encoder = encoder;
	}

	@Transactional
	public UserAccount create(String username, String rawPassword, Role role, String displayName, Long ownerId,
			Long vetId) {
		if (accounts.existsByUsername(username)) {
			throw new ConflictException("USERNAME_TAKEN", "Username " + username + " is already taken");
		}
		return accounts.save(new UserAccount(username, encoder.encode(rawPassword), role, displayName, ownerId, vetId));
	}
}
