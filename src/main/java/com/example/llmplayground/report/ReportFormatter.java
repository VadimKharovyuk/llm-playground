package com.example.llmplayground.report;

import com.example.llmplayground.persistence.Run;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Renders a run report as a console table, Markdown or CSV.
 * All numbers use {@link Locale#ROOT}: "12.5", never "12,5".
 */
public final class ReportFormatter {

    private static final List<Column> COLUMNS = List.of(
            new Column("provider", ModelStats::provider),
            new Column("model", ModelStats::model),
            new Column("calls", s -> Long.toString(s.calls())),
            new Column("ok", s -> Long.toString(s.ok())),
            new Column("errors", s -> Long.toString(s.errors())),
            new Column("empty", s -> Long.toString(s.empty())),
            new Column("avg_ms", s -> num(s.avgMs(), 0)),
            new Column("min_ms", s -> num(s.minMs())),
            new Column("max_ms", s -> num(s.maxMs())),
            new Column("p50_ms", s -> num(s.p50Ms(), 0)),
            new Column("ttft_ms", s -> num(s.avgTtftMs(), 0)),
            new Column("prompt_tok", s -> num(s.avgPromptTokens(), 1)),
            new Column("compl_tok", s -> num(s.avgCompletionTokens(), 1)),
            new Column("tok_per_s", s -> num(s.avgTokensPerSecond(), 1)),
            new Column("cost_usd", s -> money(s.totalCostUsd())),
            new Column("cold_ms", s -> num(s.coldStartMs())));

    private ReportFormatter() {
    }

    public static String console(Run run, List<ModelStats> stats) {
        List<String> headers = COLUMNS.stream().map(Column::header).toList();
        List<List<String>> rows = stats.stream()
                .map(s -> COLUMNS.stream().map(c -> c.value().apply(s)).toList())
                .toList();
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator()).append(header(run)).append(System.lineSeparator());
        appendConsoleRow(sb, headers, widths);
        StringBuilder rule = new StringBuilder();
        for (int width : widths) {
            rule.append("-".repeat(width)).append("  ");
        }
        sb.append(rule.toString().stripTrailing()).append(System.lineSeparator());
        rows.forEach(row -> appendConsoleRow(sb, row, widths));
        sb.append(footnote());
        return sb.toString();
    }

    public static String markdown(Run run, List<ModelStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LLM playground — run #").append(run.getId()).append("\n\n");
        sb.append("| Parameter | Value |\n|---|---|\n");
        for (String[] p : parameters(run)) {
            sb.append("| ").append(p[0]).append(" | ").append(escapeMd(p[1])).append(" |\n");
        }
        sb.append("\n## Results by model\n\n|");
        COLUMNS.forEach(c -> sb.append(' ').append(c.header()).append(" |"));
        sb.append("\n|");
        for (int i = 0; i < COLUMNS.size(); i++) {
            sb.append(i < 2 ? "---|" : "---:|");
        }
        sb.append('\n');
        for (ModelStats s : stats) {
            sb.append('|');
            COLUMNS.forEach(c -> sb.append(' ').append(escapeMd(c.value().apply(s))).append(" |"));
            sb.append('\n');
        }
        sb.append('\n').append(footnote());
        return sb.toString();
    }

    public static String statsCsv(List<ModelStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", COLUMNS.stream().map(Column::header).toList())).append('\n');
        for (ModelStats s : stats) {
            List<String> values = new ArrayList<>();
            COLUMNS.forEach(c -> values.add(csv(c.value().apply(s))));
            sb.append(String.join(",", values)).append('\n');
        }
        return sb.toString();
    }

    public static String callsCsv(List<CallRow> calls) {
        StringBuilder sb = new StringBuilder("id,provider,model,prompt,repeat,started_at,duration_ms,ttft_ms,"
                + "prompt_tokens,completion_tokens,total_tokens,tokens_per_second,finish_reason,cost_usd,"
                + "cold_start,status,error\n");
        for (CallRow c : calls) {
            sb.append(String.join(",", List.of(
                    Long.toString(c.id()), csv(c.provider()), csv(c.model()), csv(c.promptName()),
                    Integer.toString(c.repeatNo()), c.startedAt() == null ? "" : c.startedAt().toString(),
                    Long.toString(c.durationMs()), raw(c.ttftMs()), raw(c.promptTokens()), raw(c.completionTokens()),
                    raw(c.totalTokens()), c.tokensPerSecond() == null ? "" : num(c.tokensPerSecond(), 2),
                    csv(c.finishReason()), c.costUsd() == null ? "" : c.costUsd().stripTrailingZeros().toPlainString(),
                    Boolean.toString(c.coldStart()), csv(c.status()), csv(c.errorMessage()))));
            sb.append('\n');
        }
        return sb.toString();
    }

    static String num(Double value, int decimals) {
        return value == null ? "-" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    static String num(Long value) {
        return value == null ? "-" : Long.toString(value);
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static String header(Run run) {
        StringBuilder sb = new StringBuilder("=== Run #" + run.getId() + " ===");
        for (String[] p : parameters(run)) {
            sb.append(System.lineSeparator()).append(String.format(Locale.ROOT, "%-16s %s", p[0] + ":", p[1]));
        }
        return sb.append(System.lineSeparator()).toString();
    }

    private static List<String[]> parameters(Run run) {
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"started", String.valueOf(run.getStartedAt())});
        params.add(new String[]{"finished", run.getFinishedAt() == null ? "-" : run.getFinishedAt().toString()});
        params.add(new String[]{"models", run.getModels()});
        params.add(new String[]{"prompts", run.getPrompts() == null ? "-" : run.getPrompts()});
        params.add(new String[]{"temperature", run.getTemperature() == null ? "-"
                : String.format(Locale.ROOT, "%s", run.getTemperature())});
        params.add(new String[]{"max tokens", run.getMaxTokens() == null ? "-" : run.getMaxTokens().toString()});
        params.add(new String[]{"runs per prompt", Integer.toString(run.getRunsPerPrompt())});
        params.add(new String[]{"streaming", Boolean.toString(run.isStreaming())});
        params.add(new String[]{"app version", run.getAppVersion() == null ? "-" : run.getAppVersion()});
        if (run.getComment() != null) {
            params.add(new String[]{"comment", run.getComment()});
        }
        return params;
    }

    private static String footnote() {
        return "Times in ms over measured OK calls (warm-up and model reloads excluded); "
                + "ttft = time to first token (streaming); "
                + "tok_per_s = completion tokens per second (Ollama: eval_count/eval_duration); "
                + "cold_ms = Ollama warm-up call; cost includes all calls." + System.lineSeparator();
    }

    private static void appendConsoleRow(StringBuilder sb, List<String> values, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            String format = i < 2 ? "%-" + widths[i] + "s  " : "%" + widths[i] + "s  ";
            line.append(String.format(Locale.ROOT, format, values.get(i)));
        }
        sb.append(line.toString().stripTrailing()).append(System.lineSeparator());
    }

    private static String raw(Number value) {
        return value == null ? "" : value.toString();
    }

    static String csv(String value) {
        if (value == null || value.equals("-")) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String escapeMd(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private record Column(String header, Function<ModelStats, String> value) {
    }
}
