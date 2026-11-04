package com.example.llmplayground.web;

import com.example.llmplayground.NotFoundException;
import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.llm.provider.OpenRouterProvider;
import com.example.llmplayground.persistence.LlmCall;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.prompt.PromptService;
import com.example.llmplayground.report.ReportFormatter;
import com.example.llmplayground.report.ReportRepository;
import com.example.llmplayground.runner.ModelCatalog;
import com.example.llmplayground.runner.RunForm;
import com.example.llmplayground.runner.RunService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** New comparison form, run list, run page with answers side by side, ratings and exports. */
@Controller
public class RunController {

    private final RunService runService;
    private final RunRepository runRepository;
    private final LlmCallRepository callRepository;
    private final PromptRepository promptRepository;
    private final PromptService promptService;
    private final ReportRepository reportRepository;
    private final ModelCatalog modelCatalog;
    private final PlaygroundProperties properties;
    private final OpenRouterProvider openRouter;

    public RunController(RunService runService, RunRepository runRepository, LlmCallRepository callRepository,
                         PromptRepository promptRepository, PromptService promptService,
                         ReportRepository reportRepository, ModelCatalog modelCatalog,
                         PlaygroundProperties properties, OpenRouterProvider openRouter) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.callRepository = callRepository;
        this.promptRepository = promptRepository;
        this.promptService = promptService;
        this.reportRepository = reportRepository;
        this.modelCatalog = modelCatalog;
        this.properties = properties;
        this.openRouter = openRouter;
    }

    // ---------------------------------------------------------------- new comparison

    @GetMapping("/")
    public String newRun(@RequestParam(required = false) Long from, @RequestParam(required = false) Long prompt,
                         Model model) {
        RunForm form = runService.defaultForm();
        if (from != null) {
            Run run = findRun(from);
            form.setModels(new ArrayList<>(Arrays.asList(run.getModels().split(","))));
            form.setPromptIds(callRepository.findByRunIdOrderByIdAsc(from).stream()
                    .map(LlmCall::getPromptId).filter(Objects::nonNull).distinct().toList());
            form.setRunsPerPrompt(run.getRunsPerPrompt());
            form.setTemperature(run.getTemperature());
            form.setMaxTokens(run.getMaxTokens());
            form.setStreaming(run.isStreaming());
            form.setComment(run.getComment());
        }
        if (prompt != null) {
            form.setPromptIds(List.of(prompt));
        }
        return formPage(form, null, model);
    }

    @PostMapping("/runs")
    public String startRun(@ModelAttribute("form") RunForm form, Model model) {
        try {
            return "redirect:/runs/" + runService.start(form);
        } catch (IllegalArgumentException e) {
            return formPage(form, e.getMessage(), model);
        }
    }

    private String formPage(RunForm form, String error, Model model) {
        boolean ollamaOn = properties.isEnabled(ProviderType.OLLAMA);
        boolean openRouterOn = properties.isEnabled(ProviderType.OPENROUTER);
        // only models of enabled providers are offered; e.g. "Repeat" of an old OpenRouter run drops them
        form.setModels(new ArrayList<>(form.getModels().stream().filter(properties::isEnabled).toList()));
        Set<String> options = new LinkedHashSet<>(properties.enabledModels());
        if (ollamaOn) {
            options.addAll(modelCatalog.ollamaModels());
        }
        options.addAll(form.getModels());
        List<String> suggestions = new ArrayList<>();
        if (openRouterOn) {
            suggestions.addAll(modelCatalog.openRouterModels());
        }
        model.addAttribute("form", form);
        model.addAttribute("error", error);
        model.addAttribute("modelOptions", options);
        model.addAttribute("configuredModels", Set.copyOf(properties.models()));
        model.addAttribute("modelSuggestions", suggestions);
        model.addAttribute("prompts", promptService.all());
        model.addAttribute("overrides", properties.modelOverrides().stream()
                .filter(o -> o.model() != null && properties.isEnabled(o.model())).toList());
        model.addAttribute("providers", runService.enabledPrefixes());
        model.addAttribute("openRouterOn", openRouterOn);
        model.addAttribute("modelPlaceholder", openRouterOn && ollamaOn
                ? "openrouter:google/gemini-3-flash-preview, ollama:qwen3:8b"
                : openRouterOn ? "openrouter:google/gemini-3-flash-preview" : "ollama:qwen3:8b");
        model.addAttribute("openRouterKey", openRouter.isConfigured());
        model.addAttribute("active", "new");
        return "runs/new";
    }

    // ---------------------------------------------------------------- run list and page

    @GetMapping("/runs")
    public String runs(Model model) {
        model.addAttribute("runs", runRepository.findTop200ByOrderByIdDesc());
        model.addAttribute("totals", reportRepository.runTotals());
        model.addAttribute("active", "runs");
        return "runs/list";
    }

    @GetMapping("/runs/{id}")
    public String run(@PathVariable long id, Model model) {
        fillRun(id, model);
        model.addAttribute("active", "runs");
        return "runs/detail";
    }

    /** The changing part of the run page; polled while the run is in progress. */
    @GetMapping("/runs/{id}/live")
    public String live(@PathVariable long id, Model model) {
        fillRun(id, model);
        return "runs/detail :: live";
    }

    private void fillRun(long id, Model model) {
        Run run = findRun(id);
        List<LlmCall> calls = callRepository.findByRunIdOrderByIdAsc(id).stream()
                .filter(c -> c.getRepeatNo() > 0)
                .toList();
        model.addAttribute("run", run);
        model.addAttribute("progress", runService.progress(run));
        model.addAttribute("stats", reportRepository.modelStats(id));
        model.addAttribute("groups", groupByPrompt(calls));
    }

    private List<PromptGroup> groupByPrompt(List<LlmCall> calls) {
        Set<Long> promptIds = calls.stream().map(LlmCall::getPromptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Prompt> prompts = promptRepository.findAllById(promptIds).stream()
                .collect(Collectors.toMap(Prompt::getId, Function.identity()));
        Map<String, List<LlmCall>> byPrompt = new LinkedHashMap<>();
        for (LlmCall call : calls) {
            byPrompt.computeIfAbsent(call.getPromptName(), k -> new ArrayList<>()).add(call);
        }
        List<PromptGroup> groups = new ArrayList<>();
        byPrompt.forEach((name, list) -> {
            Long promptId = list.get(0).getPromptId();
            Prompt prompt = promptId == null ? null : prompts.get(promptId);
            groups.add(new PromptGroup(promptId, name, prompt == null ? null : prompt.getText(), list));
        });
        return groups;
    }

    public record PromptGroup(Long promptId, String name, String text, List<LlmCall> calls) {
    }

    // ---------------------------------------------------------------- delete

    /** Deletes one run with all its answers; from the run page or a row of the run list. */
    @PostMapping("/runs/{id}/delete")
    public String deleteRun(@PathVariable long id, @RequestParam(required = false) String back,
                            RedirectAttributes redirect) {
        findRun(id);
        RunService.DeleteResult result = runService.delete(List.of(id));
        if (!result.skipped().isEmpty()) {
            redirect.addFlashAttribute("error", "Прогон #" + id + " ещё выполняется, удалить его нельзя");
            return "list".equals(back) ? "redirect:/runs" : "redirect:/runs/" + id;
        }
        redirect.addFlashAttribute("message", "Прогон #" + id + " удалён вместе с ответами");
        return "redirect:/runs";
    }

    /** Deletes the runs checked in the run list. */
    @PostMapping("/runs/delete")
    public String deleteRuns(@RequestParam(name = "ids", required = false) List<Long> ids,
                             RedirectAttributes redirect) {
        if (ids == null || ids.isEmpty()) {
            redirect.addFlashAttribute("error", "Отметьте прогоны, которые нужно удалить");
            return "redirect:/runs";
        }
        RunService.DeleteResult result = runService.delete(ids);
        if (!result.deleted().isEmpty()) {
            redirect.addFlashAttribute("message", "Удалено прогонов: " + result.deleted().size()
                    + " (" + joinIds(result.deleted()) + ")");
        }
        if (!result.skipped().isEmpty()) {
            redirect.addFlashAttribute("error", "Ещё выполняются, не удалены: " + joinIds(result.skipped()));
        }
        return "redirect:/runs";
    }

    private static String joinIds(List<Long> ids) {
        return ids.stream().map(id -> "#" + id).collect(Collectors.joining(", "));
    }

    // ---------------------------------------------------------------- rating

    @PostMapping("/calls/{id}/rating")
    public String rate(@PathVariable long id, @RequestParam(required = false) Short rating,
                       @RequestParam(required = false) String note,
                       @RequestParam(required = false) String back) {
        LlmCall call = callRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Call #" + id + " not found"));
        if (rating != null && (rating < 1 || rating > 5)) {
            rating = null;
        }
        call.setRating(rating);
        call.setRatingNote(note == null || note.isBlank() ? null : note.strip());
        callRepository.save(call);
        String target = back != null && back.startsWith("/") && !back.startsWith("//")
                ? back
                : "/runs/" + call.getRunId();
        return "redirect:" + target + "#call-" + id;
    }

    // ---------------------------------------------------------------- exports

    @GetMapping("/runs/{id}/report.md")
    public ResponseEntity<byte[]> markdown(@PathVariable long id) {
        Run run = findRun(id);
        return download("run-" + id + ".md", MediaType.parseMediaType("text/markdown; charset=UTF-8"),
                ReportFormatter.markdown(run, reportRepository.modelStats(id)));
    }

    @GetMapping("/runs/{id}/report.csv")
    public ResponseEntity<byte[]> statsCsv(@PathVariable long id) {
        findRun(id);
        return download("run-" + id + ".csv", MediaType.parseMediaType("text/csv; charset=UTF-8"),
                ReportFormatter.statsCsv(reportRepository.modelStats(id)));
    }

    @GetMapping("/runs/{id}/calls.csv")
    public ResponseEntity<byte[]> callsCsv(@PathVariable long id) {
        findRun(id);
        return download("run-" + id + "-calls.csv", MediaType.parseMediaType("text/csv; charset=UTF-8"),
                ReportFormatter.callsCsv(reportRepository.calls(id)));
    }

    private static ResponseEntity<byte[]> download(String fileName, MediaType type, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(type)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private Run findRun(long id) {
        return runRepository.findById(id).orElseThrow(() -> new NotFoundException("Run #" + id + " not found"));
    }
}
