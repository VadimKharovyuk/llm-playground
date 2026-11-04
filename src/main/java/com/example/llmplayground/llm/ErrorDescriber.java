package com.example.llmplayground.llm;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Turns an exception into a short human-readable reason.
 * Spring wraps a read timeout into "Error while extracting response for type ..." —
 * here the cause chain is walked and a timeout is reported as a timeout.
 */
public final class ErrorDescriber {

    static final int MAX_LENGTH = 2000;

    private ErrorDescriber() {
    }

    public static String describe(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        List<Throwable> chain = causeChain(error);
        Throwable root = chain.get(chain.size() - 1);

        for (Throwable t : chain) {
            if (t instanceof HttpConnectTimeoutException) {
                return limit("TIMEOUT (connect): " + messageOf(t));
            }
        }
        for (Throwable t : chain) {
            if (isTimeout(t)) {
                return limit("TIMEOUT: " + messageOf(t));
            }
        }
        for (Throwable t : chain) {
            if (t instanceof ConnectException) {
                return limit("CONNECTION REFUSED: " + messageOf(t) + " (is the server running?)");
            }
            if (t instanceof UnknownHostException) {
                return limit("UNKNOWN HOST: " + messageOf(t));
            }
        }

        String top = error.getClass().getSimpleName() + ": " + messageOf(error);
        if (root == error) {
            return limit(top);
        }
        return limit(top + " | root cause " + root.getClass().getSimpleName() + ": " + messageOf(root));
    }

    private static boolean isTimeout(Throwable t) {
        if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException || t instanceof TimeoutException) {
            return true;
        }
        // Netty's ReadTimeoutException / WriteTimeoutException without a compile-time dependency on Netty
        return t.getClass().getName().endsWith("TimeoutException");
    }

    private static List<Throwable> causeChain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        Throwable current = error;
        while (current != null && !chain.contains(current) && chain.size() < 20) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }

    private static String messageOf(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message.strip();
    }

    private static String limit(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + "…";
    }
}
