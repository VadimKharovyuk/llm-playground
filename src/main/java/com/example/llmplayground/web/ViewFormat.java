package com.example.llmplayground.web;

import com.example.llmplayground.report.ReportFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Number and date formatting for templates: {@code ${@fmt.ms(x)}}. Always Locale.ROOT. */
@Component("fmt")
public class ViewFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public String ms(Number value) {
        if (value == null) {
            return "–";
        }
        double ms = value.doubleValue();
        return ms >= 10_000
                ? String.format(Locale.ROOT, "%.1f s", ms / 1000)
                : String.format(Locale.ROOT, "%.0f ms", ms);
    }

    public String num(Number value, int decimals) {
        return value == null ? "–" : String.format(Locale.ROOT, "%." + decimals + "f", value.doubleValue());
    }

    public String money(BigDecimal value) {
        if (value == null) {
            return "–";
        }
        if (value.signum() == 0) {
            return "$0";
        }
        return "$" + ReportFormatter.money(value);
    }

    public String dateTime(Instant value) {
        return value == null ? "–" : DATE_TIME.format(value);
    }

    public String stars(Number rating) {
        if (rating == null) {
            return "–";
        }
        return String.format(Locale.ROOT, "%.1f ★", rating.doubleValue());
    }
}
