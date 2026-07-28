package io.github.aimi.rag.workflow.query.model;

import lombok.Data;

@Data
public class SearchResult {

    private String content;
    private float score;
    private String documentId;
    private String metadata;

    public SearchResult() {}

    public SearchResult(String content, float score) {
        this.content = content;
        this.score = score;
    }

    public SearchResult(String content, float score, String documentId) {
        this.content = content;
        this.score = score;
        this.documentId = documentId;
    }
}
