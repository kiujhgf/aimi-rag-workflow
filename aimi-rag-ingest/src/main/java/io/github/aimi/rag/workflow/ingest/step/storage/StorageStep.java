package io.github.aimi.rag.workflow.ingest.step.storage;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.exception.StepValidationException;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import io.github.aimi.rag.workflow.core.resolver.OutputResolver;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.core.step.Step;
import io.github.aimi.rag.workflow.ingest.model.StorageItem;
import io.github.aimi.rag.workflow.ingest.model.StorageResult;

import java.util.List;

public abstract class StorageStep<T extends StorageItem> extends AbstractStep<List<T>, StorageResult> {

    protected StorageStep() {
        super();
    }

    protected StorageStep(InputResolver<List<T>> inputResolver) {
        super(inputResolver);
    }

    protected StorageStep(InputResolver<List<T>> inputResolver, OutputResolver<StorageResult> outputResolver) {
        super(inputResolver, outputResolver);
    }

    @Override
    public StepType getType() {
        return StepType.STORAGE;
    }

    @Override
    public FlowCategory getCategory() {
        return FlowCategory.INGEST;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<T>> getInputType() {
        return (Class<List<T>>) (Class<?>) List.class;
    }

    @Override
    protected abstract StorageResult doProcess(List<T> input, io.github.aimi.rag.workflow.core.model.FlowContext context) throws StepExecuteException;
}