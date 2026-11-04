package com.example.llmplayground.web;

import com.example.llmplayground.report.ReportRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Leaderboard: every model over all runs. */
@Controller
public class ModelController {

    private final ReportRepository reportRepository;

    public ModelController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @GetMapping("/models")
    public String models(Model model) {
        model.addAttribute("stats", reportRepository.leaderboard());
        model.addAttribute("active", "models");
        return "models";
    }
}
