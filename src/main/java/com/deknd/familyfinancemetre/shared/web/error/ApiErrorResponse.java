package com.deknd.familyfinancemetre.shared.web.error;

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

