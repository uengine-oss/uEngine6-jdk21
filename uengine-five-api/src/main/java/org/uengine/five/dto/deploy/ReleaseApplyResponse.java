package org.uengine.five.dto.deploy;

import java.util.ArrayList;
import java.util.List;

/** POST /deploy/apply 배치 응답. */
public class ReleaseApplyResponse {

    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<ReleaseApplyItemResult> results = new ArrayList<>();

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public boolean isAllSucceeded() {
        return totalCount > 0 && failedCount == 0;
    }

    public boolean isPartialSuccess() {
        return successCount > 0 && failedCount > 0;
    }

    public List<ReleaseApplyItemResult> getResults() {
        return results;
    }

    public void setResults(List<ReleaseApplyItemResult> results) {
        this.results = results != null ? results : new ArrayList<>();
    }
}
