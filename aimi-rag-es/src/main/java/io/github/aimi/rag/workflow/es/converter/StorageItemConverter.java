package io.github.aimi.rag.workflow.es.converter;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.StorageItem;

import java.util.Map;

@FunctionalInterface
public interface StorageItemConverter {

    Map<String, Object> toDoc(StorageItem item, FlowContext context);
}