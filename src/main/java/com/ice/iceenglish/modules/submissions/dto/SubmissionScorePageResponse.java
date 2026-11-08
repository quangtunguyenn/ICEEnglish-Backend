package com.ice.iceenglish.modules.submissions.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubmissionScorePageResponse {
    private List<SubmissionScoreResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
