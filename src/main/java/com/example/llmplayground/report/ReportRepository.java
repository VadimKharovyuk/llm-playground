package com.example.llmplayground.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Report queries in plain SQL: aggregation happens in PostgreSQL and response_text is never selected. */
@Repository
public class ReportRepository {

    /** Timing stats: measured calls (not warm-up), model already in memory, successful. */
    private static final String MEASURED_OK = "repeat_no > 0 and not cold_start and status = 'OK'";

    private static final String STATS_SQL = """
            select provider, model,
                   count(*) filter (where repeat_no > 0)                          as calls,
                   count(*) filter (where repeat_no > 0 and status = 'OK')        as ok,
                   count(*) filter (where repeat_no > 0 and status = 'ERROR')     as errors,
                   count(*) filter (where repeat_no > 0 and status = 'EMPTY')     as empty,
                   avg(duration_ms)       filter (where %1$s)                     as avg_ms,
                   min(duration_ms)       filter (where %1$s)                     as min_ms,
                   max(duration_ms)       filter (where %1$s)                     as max_ms,
                   percentile_cont(0.5) within group (order by duration_ms) filter (where %1$s) as p50_ms,
                   avg(ttft_ms)           filter (where %1$s)                     as avg_ttft_ms,
                   avg(prompt_tokens)     filter (where %1$s)                     as avg_prompt_tokens,
                   avg(completion_tokens) filter (where %1$s)                     as avg_completion_tokens,
                   avg(tokens_per_second) filter (where %1$s)                     as avg_tps,
                   coalesce(sum(cost_usd), 0)                                     as total_cost,
                   max(duration_ms)       filter (where repeat_no = 0)            as cold_start_ms,
                   avg(rating)                                                    as avg_rating,
                   count(rating)                                                  as rated,
                   count(distinct run_id)                                         as runs,
                   max(started_at)                                                as last_used
              from llm_call
             where %2$s
             group by provider, model
             order by %3$s
            """;

    private static final String CALLS_SQL = """
            select id, provider, model, prompt_name, repeat_no, started_at, duration_ms, ttft_ms,
                   prompt_tokens, completion_tokens, total_tokens, tokens_per_second, finish_reason,
                   cost_usd, cold_start, status, error_message, rating
              from llm_call
             where run_id = ?
             order by id
            """;

    private static final String RUN_TOTALS_SQL = """
            select run_id,
                   count(*)                                  as calls,
                   count(*) filter (where status = 'ERROR')  as errors,
                   count(*) filter (where status = 'EMPTY')  as empty,
                   coalesce(sum(cost_usd), 0)                as cost
              from llm_call
             group by run_id
            """;

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Per model within one run, in the order the models were called. */
    public List<ModelStats> modelStats(long runId) {
        return jdbc.query(STATS_SQL.formatted(MEASURED_OK, "run_id = ?", "min(id)"), this::mapStats, runId);
    }

    /** Per model over all answers to one prompt, best rated first. */
    public List<ModelStats> promptStats(long promptId) {
        return jdbc.query(STATS_SQL.formatted(MEASURED_OK, "prompt_id = ?", "avg(rating) desc nulls last, model"),
                this::mapStats, promptId);
    }

    /** Leaderboard over all runs, best rated first. */
    public List<ModelStats> leaderboard() {
        return jdbc.query(STATS_SQL.formatted(MEASURED_OK, "true", "avg(rating) desc nulls last, model"),
                this::mapStats);
    }

    public List<CallRow> calls(long runId) {
        return jdbc.query(CALLS_SQL, (rs, i) -> {
            Timestamp started = rs.getTimestamp("started_at");
            return new CallRow(
                    rs.getLong("id"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getString("prompt_name"),
                    rs.getInt("repeat_no"),
                    started == null ? null : started.toInstant(),
                    rs.getLong("duration_ms"),
                    getLong(rs, "ttft_ms"),
                    getInt(rs, "prompt_tokens"),
                    getInt(rs, "completion_tokens"),
                    getInt(rs, "total_tokens"),
                    getDouble(rs, "tokens_per_second"),
                    rs.getString("finish_reason"),
                    rs.getBigDecimal("cost_usd"),
                    rs.getBoolean("cold_start"),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    getInt(rs, "rating"));
        }, runId);
    }

    /** Totals per run for the run list. */
    public Map<Long, RunTotals> runTotals() {
        Map<Long, RunTotals> totals = new HashMap<>();
        jdbc.query(RUN_TOTALS_SQL, rs -> {
            totals.put(rs.getLong("run_id"), new RunTotals(rs.getLong("calls"), rs.getLong("errors"),
                    rs.getLong("empty"), rs.getBigDecimal("cost")));
        });
        return totals;
    }

    public record RunTotals(long calls, long errors, long empty, BigDecimal cost) {
    }

    private ModelStats mapStats(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastUsed = rs.getTimestamp("last_used");
        return new ModelStats(
                rs.getString("provider"),
                rs.getString("model"),
                rs.getLong("calls"),
                rs.getLong("ok"),
                rs.getLong("errors"),
                rs.getLong("empty"),
                getDouble(rs, "avg_ms"),
                getLong(rs, "min_ms"),
                getLong(rs, "max_ms"),
                getDouble(rs, "p50_ms"),
                getDouble(rs, "avg_ttft_ms"),
                getDouble(rs, "avg_prompt_tokens"),
                getDouble(rs, "avg_completion_tokens"),
                getDouble(rs, "avg_tps"),
                rs.getBigDecimal("total_cost"),
                getLong(rs, "cold_start_ms"),
                getDouble(rs, "avg_rating"),
                rs.getLong("rated"),
                rs.getLong("runs"),
                lastUsed == null ? null : lastUsed.toInstant());
    }

    private static Double getDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
