package com.example.llmplayground.report;

import com.example.llmplayground.persistence.Run;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFormatterTest {

    private Locale previous;

    @BeforeEach
    void russianLocale() {
        previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ru-RU"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previous);
    }

    private static final ModelStats STATS = new ModelStats("OLLAMA", "llama3.2:3b", 6, 5, 1, 0,
            1234.56, 900L, 2100L, 1200.0, 180.4, 25.0, 12.5, 48.25, new BigDecimal("0.0001234567"), 5400L);

    private static Run run() {
        Run run = new Run();
        run.setId(3L);
        run.setStartedAt(Instant.parse("2026-11-04T10:00:00Z"));
        run.setModels("ollama:llama3.2:3b");
        run.setTemperature(0.2);
        run.setMaxTokens(1024);
        run.setRunsPerPrompt(3);
        return run;
    }

    @Test
    void numbersUseDotEvenOnRussianLocale() {
        String console = ReportFormatter.console(run(), List.of(STATS));
        assertThat(console).contains("12.5").contains("48.3").contains("1235").contains("0.000123")
                .doesNotContain("12,5").doesNotContain("48,3");
    }

    @Test
    void markdownHasTableRowPerModel() {
        String md = ReportFormatter.markdown(run(), List.of(STATS));
        assertThat(md).contains("# LLM playground — run #3")
                .contains("| OLLAMA | llama3.2:3b | 6 | 5 | 1 | 0 | 1235 | 900 | 2100 |");
    }

    @Test
    void csvQuotesValuesWithCommas() {
        assertThat(ReportFormatter.csv("a,b")).isEqualTo("\"a,b\"");
        assertThat(ReportFormatter.csv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportFormatter.statsCsv(List.of(STATS)).lines().skip(1).findFirst().orElseThrow())
                .startsWith("OLLAMA,llama3.2:3b,6,5,1,0,1235,900,2100,1200,180,25.0,12.5,48.3,0.000123,5400");
    }
}
