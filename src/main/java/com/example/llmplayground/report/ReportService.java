package com.example.llmplayground.report;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Builds the report of a run from the database: console summary plus Markdown/CSV files. */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final RunRepository runRepository;
    private final ReportRepository reportRepository;
    private final PlaygroundProperties properties;

    public ReportService(RunRepository runRepository, ReportRepository reportRepository,
                         PlaygroundProperties properties) {
        this.runRepository = runRepository;
        this.reportRepository = reportRepository;
        this.properties = properties;
    }

    public Run resolveRun(Long runId) {
        if (runId != null) {
            return runRepository.findById(runId)
                    .orElseThrow(() -> new IllegalArgumentException("Run #" + runId + " not found"));
        }
        return runRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("No runs in the database yet"));
    }

    public String consoleSummary(Run run) {
        return ReportFormatter.console(run, reportRepository.modelStats(run.getId()));
    }

    /** Writes run-N.md, run-N.csv (per model) and run-N-calls.csv (per call). */
    public List<Path> export(Run run) {
        List<ModelStats> stats = reportRepository.modelStats(run.getId());
        List<CallRow> calls = reportRepository.calls(run.getId());
        Path dir = Path.of(properties.report().outputDir());
        String base = "run-" + run.getId();
        try {
            Files.createDirectories(dir);
            Path md = Files.writeString(dir.resolve(base + ".md"), ReportFormatter.markdown(run, stats),
                    StandardCharsets.UTF_8);
            Path csv = Files.writeString(dir.resolve(base + ".csv"), ReportFormatter.statsCsv(stats),
                    StandardCharsets.UTF_8);
            Path callsCsv = Files.writeString(dir.resolve(base + "-calls.csv"), ReportFormatter.callsCsv(calls),
                    StandardCharsets.UTF_8);
            List<Path> files = List.of(md, csv, callsCsv);
            files.forEach(f -> log.info("Report written: {}", f.toAbsolutePath()));
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report to " + dir.toAbsolutePath(), e);
        }
    }
}
