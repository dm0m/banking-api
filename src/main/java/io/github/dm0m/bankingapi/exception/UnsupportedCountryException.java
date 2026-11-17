package io.github.dm0m.bankingapi.exception;

public class UnsupportedCountryException extends RuntimeException {
    public UnsupportedCountryException(String country) {
        super("Accounts cannot be opened for country '" + country + "'");
    }
}
