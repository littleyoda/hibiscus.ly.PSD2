package de.open4me.hibiscus.psd2.api;

public class EnableBankingException extends Exception
{
    private static final long serialVersionUID = 1L;
    private static final String ASPSP_STATUS_HINT = "Bitte Verfügbarkeit des Services prüfen: "
            + "https://enablebanking.com/cp/aspsps";

    private final int status;
    private final String code;

    public EnableBankingException(int status, String code, String message)
    {
        super(withStatusHint(code, message));
        this.status = status;
        this.code = code;
    }

    private static String withStatusHint(String code, String message)
    {
        if (!"ASPSP_ERROR".equals(code) || message == null || message.contains(ASPSP_STATUS_HINT))
            return message;
        return message + "\n" + ASPSP_STATUS_HINT;
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

    public boolean isRateLimitExceeded()
    {
        return "ASPSP_RATE_LIMIT_EXCEEDED".equals(code);
    }
}
