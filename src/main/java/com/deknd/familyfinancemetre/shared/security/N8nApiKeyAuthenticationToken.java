package com.deknd.familyfinancemetre.shared.security;

import com.deknd.familyfinancemetre.shared.security.principal.IntegrationPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class N8nApiKeyAuthenticationToken extends AbstractAuthenticationToken {

	private final IntegrationPrincipal principal;
	private final String credentials;

	public N8nApiKeyAuthenticationToken(String credentials) {
		super(AuthorityUtils.NO_AUTHORITIES);
		this.principal = null;
		this.credentials = credentials;
		setAuthenticated(false);
	}

	public N8nApiKeyAuthenticationToken(IntegrationPrincipal principal) {
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


