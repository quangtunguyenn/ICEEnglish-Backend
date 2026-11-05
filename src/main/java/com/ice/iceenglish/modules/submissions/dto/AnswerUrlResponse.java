package com.ice.iceenglish.modules.submissions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnswerUrlResponse {
    @JsonProperty("answer_url")
    String answerUrl;
}
