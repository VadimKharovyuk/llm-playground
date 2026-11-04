package com.example.llmplayground.persistence;

public enum RunStatus {
    QUEUED, RUNNING, DONE, FAILED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
