package com.deknd.familyfinancemetre.shared.security;

import org.springframework.security.core.AuthenticationException;

public class HeaderAuthenticationException extends AuthenticationException {

	private final String code;
	private final String responseMessage;

	public HeaderAuthenticationException(String code, String responseMessage) {
		super(responseMessage);
		this.code = code;
		this.responseMessage = responseMessage;
	}

	public String getCode() {
		return code;
	}

	public String getResponseMessage() {
		return responseMessage;
	}
}

