package de.open4me.hibiscus.psd2.api;

public class EnableBankingException extends Exception
{
    private final int status;
    private final String code;

    public EnableBankingException(int status, String code, String message)
    {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus()
    {
        return status;
    }

    public String getCode()
    {
        return code;
    }

    public boolean isExpiredSession()
    {
        return "EXPIRED_SESSION".equals(code) || "CLOSED_SESSION".equals(code);
    }
}
