package com.deknd.familyfinancemetre.shared.security;

import com.deknd.familyfinancemetre.shared.security.principal.DevicePrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class DeviceAuthenticationToken extends AbstractAuthenticationToken {

	private final DevicePrincipal principal;
	private final String credentials;

	public DeviceAuthenticationToken(String credentials) {
		super(AuthorityUtils.NO_AUTHORITIES);
		this.principal = null;
		this.credentials = credentials;
		setAuthenticated(false);
	}

	public DeviceAuthenticationToken(DevicePrincipal principal) {
		super(AuthorityUtils.NO_AUTHORITIES);
		this.principal = principal;
		this.credentials = null;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return credentials;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}


