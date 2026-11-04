package com.example.llmplayground.web;

import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.prompt.PromptService;
import com.example.llmplayground.report.ReportRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Prompt library: write tasks, see how every model answered a task across all runs. */
@Controller
public class PromptController {

    private final PromptService promptService;
    private final LlmCallRepository callRepository;
    private final ReportRepository reportRepository;

    public PromptController(PromptService promptService, LlmCallRepository callRepository,
                            ReportRepository reportRepository) {
        this.promptService = promptService;
        this.callRepository = callRepository;
        this.reportRepository = reportRepository;
    }

    @GetMapping("/prompts")
    public String list(Model model) {
        model.addAttribute("prompts", promptService.all());
        model.addAttribute("active", "prompts");
        return "prompts/list";
    }

    @PostMapping("/prompts")
    public String create(@RequestParam(required = false) String name, @RequestParam String text,
                         RedirectAttributes redirect) {
        try {
            Prompt prompt = promptService.create(name, text);
            redirect.addFlashAttribute("message", "Промпт «" + prompt.getName() + "» сохранён");
            return "redirect:/prompts/" + prompt.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/prompts";
        }
    }

    @GetMapping("/prompts/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("prompt", promptService.get(id));
        model.addAttribute("used", promptService.isUsed(id));
        model.addAttribute("stats", reportRepository.promptStats(id));
        model.addAttribute("calls", callRepository.findTop100ByPromptIdAndRepeatNoGreaterThanOrderByIdDesc(id, 0));
        model.addAttribute("active", "prompts");
        return "prompts/detail";
    }

    @PostMapping("/prompts/{id}")
    public String update(@PathVariable long id, @RequestParam(required = false) String name,
                         @RequestParam String text, RedirectAttributes redirect) {
        try {
            Prompt saved = promptService.update(id, name, text);
            redirect.addFlashAttribute("message", saved.getId() == id
                    ? "Промпт сохранён"
                    : "У промпта уже есть ответы, поэтому изменения сохранены как новый промпт «"
                      + saved.getName() + "»");
            return "redirect:/prompts/" + saved.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/prompts/" + id;
        }
    }

    @PostMapping("/prompts/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            promptService.delete(id);
            redirect.addFlashAttribute("message", "Промпт удалён");
            return "redirect:/prompts";
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", "У промпта уже есть ответы, удалить его нельзя");
            return "redirect:/prompts/" + id;
        }
    }
}
