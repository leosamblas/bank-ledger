package com.bankledger.commons.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Standard authenticated user principal representing the authenticated identity across business services.
 * Implements both {@link Principal} and {@link UserDetails} for seamless integration with Spring Security.
 */
public record CommonAuthenticatedUser(
        UUID customerId,
        UUID accountId,
        String cpf,
        String role,
        String branch,
        String accountNumber
) implements Principal, UserDetails, Serializable {

    public CommonAuthenticatedUser(UUID customerId, UUID accountId, String branch, String accountNumber, String role) {
        this(customerId, accountId, null, role, branch, accountNumber);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getRole() {
        return role;
    }

    public String getBranch() {
        return branch;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCpf() {
        return cpf;
    }

    @Override
    public String getName() {
        return customerId != null ? customerId.toString() : (cpf != null ? cpf : "");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String effectiveRole = (role != null && !role.isBlank()) ? role : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(effectiveRole));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return cpf != null && !cpf.isBlank() ? cpf : (customerId != null ? customerId.toString() : "");
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
