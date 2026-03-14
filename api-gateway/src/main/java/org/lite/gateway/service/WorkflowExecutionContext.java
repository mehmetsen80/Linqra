package org.lite.gateway.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

public class WorkflowExecutionContext {
    @Getter
    private final Map<Integer, Object> stepResults;
    @Getter
    private final Map<String, Object> globalParams;

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicInteger jumpTarget = new AtomicInteger(-1);

    public WorkflowExecutionContext(Map<Integer, Object> stepResults, Map<String, Object> globalParams) {
        // Ensure we use thread-safe maps if not already provided
        this.stepResults = stepResults instanceof ConcurrentHashMap ? stepResults
                : new ConcurrentHashMap<>(stepResults);
        this.globalParams = globalParams instanceof ConcurrentHashMap ? globalParams
                : new ConcurrentHashMap<>(globalParams);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public void setStopped(boolean stopped) {
        this.stopped.set(stopped);
    }

    public int getJumpTarget() {
        return jumpTarget.get();
    }

    public void setJumpTarget(int target) {
        this.jumpTarget.set(target);
    }
}
