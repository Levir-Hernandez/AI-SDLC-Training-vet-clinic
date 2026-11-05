package com.vetclinic.shared.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/** 401 / 403 responses as application/problem+json, with the same shape as GlobalExceptionHandler. */
public final class JsonAuthHandlers {

	private JsonAuthHandlers() {
	}

	public static AuthenticationEntryPoint entryPoint() {
		return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
				"Authentication is required");
	}

	public static AccessDeniedHandler accessDeniedHandler() {
		return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
	}

	private static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// constants only, so no JSON escaping is needed
		response.getWriter()
			.write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
					+ status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\",\"errors\":[]}");
	}
}
