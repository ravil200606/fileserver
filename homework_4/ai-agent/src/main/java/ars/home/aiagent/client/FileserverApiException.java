package ars.home.aiagent.client;

import org.springframework.http.HttpStatusCode;

public class FileserverApiException extends RuntimeException {

    private final HttpStatusCode status;

    public FileserverApiException(HttpStatusCode status, String responseBody) {
        super("HTTP " + status.value()
                + (responseBody == null || responseBody.isBlank() ? "" : ": " + responseBody));
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
