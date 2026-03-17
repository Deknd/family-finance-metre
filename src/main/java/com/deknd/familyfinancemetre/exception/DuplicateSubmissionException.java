package com.deknd.familyfinancemetre.exception;

public class DuplicateSubmissionException extends RuntimeException {

	public static final String ERROR_CODE = "DUPLICATE_SUBMISSION";
	public static final String ERROR_MESSAGE = "Submission with this external_submission_id already exists";

	public DuplicateSubmissionException() {
		super(ERROR_MESSAGE);
	}

	public DuplicateSubmissionException(Throwable cause) {
		super(ERROR_MESSAGE, cause);
	}
}
