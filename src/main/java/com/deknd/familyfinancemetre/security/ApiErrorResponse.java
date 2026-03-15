package com.deknd.familyfinancemetre.security;

public record ApiErrorResponse(
	ErrorDetails error
) {

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(new ErrorDetails(code, message));
	}

	public record ErrorDetails(
		String code,
		String message
	) {
	}
}
