package com.example.llmplayground.runner;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** The "new comparison" form. */
@Data
public class RunForm {

    /** Checked models, {@code provider:model}. */
    private List<String> models = new ArrayList<>();
    /** More models typed by hand, separated by commas or new lines. */
    private String extraModels;

    /** Saved prompts to run. */
    private List<Long> promptIds = new ArrayList<>();
    /** A new task written in the form; saved to the prompt table. */
    private String newPromptName;
    private String newPromptText;

    private Double temperature;
    private Integer maxTokens;
    private Integer runsPerPrompt;
    private boolean streaming;
    private boolean warmup;
    private String comment;
}
