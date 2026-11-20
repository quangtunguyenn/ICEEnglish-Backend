package com.ice.iceenglish.modules.exams.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeleteDraftExamsResponse {
    private int deletedCount;
}
