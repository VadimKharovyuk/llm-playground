package com.example.llmplayground.persistence;

import com.example.llmplayground.PostgresContainerConfig;
import com.example.llmplayground.llm.CallStatus;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.report.ModelStats;
import com.example.llmplayground.report.ReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Repositories and report SQL against a real PostgreSQL with the Flyway schema. Skipped without Docker. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfig.class, ReportRepository.class})
@Testcontainers(disabledWithoutDocker = true)
class RepositoryTest {

    @Autowired
    RunRepository runRepository;
    @Autowired
    LlmCallRepository callRepository;
    @Autowired
    ReportRepository reportRepository;

    @Test
    void storesRunAndCallsAndAggregatesThem() {
        Run run = new Run();
        run.setStartedAt(Instant.now());
        run.setModels("ollama:llama3.2:3b");
        run.setRunsPerPrompt(2);
        run.setStreaming(true);
        run = runRepository.save(run);

        callRepository.save(call(run.getId(), 0, true, CallStatus.OK, 9000, "0"));      // warm-up
        callRepository.save(call(run.getId(), 1, false, CallStatus.OK, 1000, "0.001"));
        callRepository.save(call(run.getId(), 2, false, CallStatus.OK, 3000, "0.002"));
        callRepository.save(call(run.getId(), 3, false, CallStatus.ERROR, 50, null));
        callRepository.save(call(run.getId(), 4, false, CallStatus.EMPTY, 2000, "0.001"));
        callRepository.save(call(run.getId(), 5, true, CallStatus.OK, 7000, null));     // model reloaded
        callRepository.flush();

        assertThat(callRepository.countByRunId(run.getId())).isEqualTo(6);
        assertThat(runRepository.findTopByOrderByIdDesc()).map(Run::getId).contains(run.getId());

        List<ModelStats> stats = reportRepository.modelStats(run.getId());
        assertThat(stats).hasSize(1);
        ModelStats s = stats.get(0);
        assertThat(s.calls()).isEqualTo(5);
        assertThat(s.ok()).isEqualTo(3);
        assertThat(s.errors()).isEqualTo(1);
        assertThat(s.empty()).isEqualTo(1);
        assertThat(s.avgMs()).isEqualTo(2000.0);
        assertThat(s.minMs()).isEqualTo(1000L);
        assertThat(s.maxMs()).isEqualTo(3000L);
        assertThat(s.coldStartMs()).isEqualTo(9000L);
        assertThat(s.totalCostUsd()).isEqualByComparingTo("0.004");
        assertThat(reportRepository.calls(run.getId())).hasSize(6);
    }

    private static LlmCall call(Long runId, int repeat, boolean cold, CallStatus status, long ms, String cost) {
        LlmCall call = new LlmCall();
        call.setRunId(runId);
        call.setProvider(ProviderType.OLLAMA);
        call.setModel("llama3.2:3b");
        call.setPromptName("greeting");
        call.setRepeatNo(repeat);
        call.setStartedAt(Instant.now());
        call.setDurationMs(ms);
        call.setCompletionTokens(status == CallStatus.OK ? 20 : null);
        call.setTokensPerSecond(status == CallStatus.OK ? 40.0 : null);
        call.setColdStart(cold);
        call.setStatus(status);
        call.setCostUsd(cost == null ? null : new BigDecimal(cost));
        call.setResponseText(status == CallStatus.OK ? "answer" : null);
        return call;
    }
}
