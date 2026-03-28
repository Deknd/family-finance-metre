package com.deknd.familyfinancemetre.flow.dashboard.exception;

public class DashboardNotReadyException extends RuntimeException {

	public static final String ERROR_CODE = "DASHBOARD_NOT_READY";
	public static final String ERROR_MESSAGE = "Dashboard data is not available yet";

	public DashboardNotReadyException() {
		super(ERROR_MESSAGE);
	}
}

