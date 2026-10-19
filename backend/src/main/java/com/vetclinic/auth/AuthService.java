package com.vetclinic.auth;

import com.vetclinic.auth.dto.CurrentUserResponse;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.LoginResponse;
import com.vetclinic.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AuthService(UserAccountRepository accounts, PasswordEncoder encoder, TokenService tokens) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    public LoginResponse login(LoginRequest request) {
        // Same error for unknown user and wrong password so usernames cannot be probed.
        UserAccount account = accounts.findByUsername(request.username())
                .filter(a -> encoder.matches(request.password(), a.getPasswordHash()))
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Invalid username or password"));
        TokenService.IssuedToken token = tokens.issue(account);
        return new LoginResponse(token.value(), token.expiresAt(), account.getRole(), account.getDisplayName());
    }

    public CurrentUserResponse me(CurrentUser user) {
        UserAccount account = accounts.findById(user.userId()).orElseThrow(() ->
                new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Account no longer exists"));
        return new CurrentUserResponse(account.getId(), account.getUsername(), account.getRole(),
                account.getDisplayName());
    }
}
