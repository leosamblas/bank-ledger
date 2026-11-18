package com.bankledger.account.application.port.in;

/**
 * Inbound port for updating customer profile.
 */
public interface UpdateProfileUseCase {

    AccountMeResult updateProfile(UpdateProfileCommand command);
}
