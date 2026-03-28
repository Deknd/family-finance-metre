package com.deknd.familyfinancemetre.shared.web.error;

import com.deknd.familyfinancemetre.shared.web.error.ApiErrorResponse;

import java.util.List;

public record ValidationErrorResponse(
	ApiErrorResponse.ErrorDetails error,
	List<ValidationErrorDetail> details
) {

	public static ValidationErrorResponse of(String code, String message, List<ValidationErrorDetail> details) {
		return new ValidationErrorResponse(new ApiErrorResponse.ErrorDetails(code, message), details);
	}

	public record ValidationErrorDetail(
		String field,
		String message
	) {
	}
}


