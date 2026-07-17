package io.github.aimi.rag.workflow.es.converter;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.ingest.model.Embedding;
import io.github.aimi.rag.workflow.ingest.model.StorageItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultStorageItemConverter implements StorageItemConverter {

    @Override
    public Map<String, Object> toDoc(StorageItem item, FlowContext context) {
        Map<String, Object> doc = new HashMap<>(item.getMetadata());
        doc.put("content", item.getContent());
        if (item instanceof Embedding emb) {
            doc.put("_vector", toFloatList(emb.getVector()));
        }
        return doc;
    }

    private List<Float> toFloatList(float[] vector) {
        if (vector == null) {
            return List.of();
        }
        List<Float> result = new ArrayList<>(vector.length);
        for (float f : vector) {
            result.add(f);
        }
        return result;
    }
}