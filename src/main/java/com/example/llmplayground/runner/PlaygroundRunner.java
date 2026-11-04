package com.example.llmplayground.runner;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.report.ReportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Console entry point: {@code playground.mode=run|report|none}. */
@Component
public class PlaygroundRunner implements CommandLineRunner {

    private final PlaygroundProperties properties;
    private final BenchmarkService benchmarkService;
    private final ReportService reportService;

    public PlaygroundRunner(PlaygroundProperties properties, BenchmarkService benchmarkService,
                            ReportService reportService) {
        this.properties = properties;
        this.benchmarkService = benchmarkService;
        this.reportService = reportService;
    }

    @Override
    public void run(String... args) {
        switch (properties.mode()) {
            case RUN -> report(benchmarkService.execute());
            case REPORT -> report(properties.report().runId());
            case NONE -> { }
        }
    }

    private void report(Long runId) {
        Run run = reportService.resolveRun(runId);
        System.out.println(reportService.consoleSummary(run));
        reportService.export(run);
    }
}
