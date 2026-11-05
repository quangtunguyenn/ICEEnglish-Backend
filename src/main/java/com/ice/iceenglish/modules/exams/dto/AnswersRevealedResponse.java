package com.ice.iceenglish.modules.exams.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class AnswersRevealedResponse {
    UUID id;
    @JsonProperty("is_answers_revealed")
    boolean answersRevealed;
}
