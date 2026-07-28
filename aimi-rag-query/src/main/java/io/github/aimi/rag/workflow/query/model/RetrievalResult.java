package io.github.aimi.rag.workflow.query.model;

import lombok.Data;

import java.util.List;

@Data
public class RetrievalResult {

    private List<SearchResult> results;
    private int totalHits;

    public RetrievalResult() {}

    public RetrievalResult(List<SearchResult> results) {
        this.results = results;
        this.totalHits = results.size();
    }

    public RetrievalResult(List<SearchResult> results, int totalHits) {
        this.results = results;
        this.totalHits = totalHits;
    }

    public boolean isEmpty() {
        return results == null || results.isEmpty();
    }

    public int getResultSize() {
        return results == null ? 0 : results.size();
    }

    public String getContextContent() {
        return getContextContent("\n\n");
    }

    public String getContextContent(String separator) {
        if (isEmpty()) {
            return "";
        }
        return results.stream()
                .map(SearchResult::getContent)
                .reduce((a, b) -> a + separator + b)
                .orElse("");
    }

    public List<SearchResult> getTopN(int n) {
        if (isEmpty()) {
            return results;
        }
        if (results.size() <= n) {
            return results;
        }
        return results.subList(0, n);
    }

    public RetrievalResult withTopN(int n) {
        List<SearchResult> topN = getTopN(n);
        return new RetrievalResult(topN, this.totalHits);
    }
}
