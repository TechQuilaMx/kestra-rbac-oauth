package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.TaskRun;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
public class WorkerTaskResult implements HasUID {
    @NotNull
    TaskRun taskRun;

    List<TaskRun> dynamicTaskRuns;

    public WorkerTaskResult(TaskRun taskRun) {
        this(taskRun, new ArrayList<>(1)); // there are usually very few dynamic task runs, so we init the list with a capacity of 1
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return taskRun.getId();
    }
}
