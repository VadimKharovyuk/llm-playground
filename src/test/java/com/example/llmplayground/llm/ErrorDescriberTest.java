package com.example.llmplayground.llm;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorDescriberTest {

    @Test
    void readTimeoutHiddenBehindExtractingResponseIsReportedAsTimeout() {
        // What Spring AI actually throws when the HTTP read times out
        Exception error = new RestClientException("Error while extracting response for type [ChatCompletion]",
                new ResourceAccessException("I/O error on POST request", new HttpTimeoutException("request timed out")));

        assertThat(ErrorDescriber.describe(error))
                .startsWith("TIMEOUT")
                .contains("request timed out")
                .doesNotContain("Error while extracting response");
    }

    @Test
    void socketTimeoutIsTimeout() {
        Exception error = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));
        assertThat(ErrorDescriber.describe(error)).isEqualTo("TIMEOUT: Read timed out");
    }

    @Test
    void streamingIdleTimeoutIsTimeout() {
        Exception error = new RuntimeException(new TimeoutException("Did not observe any item within 120000ms"));
        assertThat(ErrorDescriber.describe(error)).startsWith("TIMEOUT: Did not observe any item");
    }

    @Test
    void connectTimeoutIsMarkedAsConnect() {
        Exception error = new ResourceAccessException("I/O error", new HttpConnectTimeoutException("HTTP connect timed out"));
        assertThat(ErrorDescriber.describe(error)).startsWith("TIMEOUT (connect)");
    }

    @Test
    void connectionRefusedHintsAtServer() {
        Exception error = new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
        assertThat(ErrorDescriber.describe(error)).startsWith("CONNECTION REFUSED: Connection refused");
    }

    @Test
    void otherErrorsKeepTopMessageAndRootCause() {
        Exception error = new IllegalStateException("HTTP 401 - invalid key",
                new IllegalArgumentException("bad header"));
        assertThat(ErrorDescriber.describe(error))
                .isEqualTo("IllegalStateException: HTTP 401 - invalid key | root cause IllegalArgumentException: bad header");
    }

    @Test
    void longMessagesAreTruncated() {
        String description = ErrorDescriber.describe(new RuntimeException("x".repeat(5000)));
        assertThat(description.length()).isLessThanOrEqualTo(ErrorDescriber.MAX_LENGTH + 1);
    }
}
