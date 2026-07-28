package io.github.aimi.rag.workflow.query.model;

import lombok.Data;

@Data
public class QueryResult {

    private String answer;
    private RetrievalResult retrievalResult;

    public QueryResult() {}

    public QueryResult(String answer) {
        this.answer = answer;
    }

    public QueryResult(String answer, RetrievalResult retrievalResult) {
        this.answer = answer;
        this.retrievalResult = retrievalResult;
    }
}
