package de.open4me.hibiscus.psd2.auth;

public record CallbackResult(String code, String error, String errorDescription)
{
    public boolean isSuccessful()
    {
        return code != null && !code.isBlank() && error == null;
    }
}
