package com.ice.iceenglish.modules.learning.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class VocabularyResponseDto {
    UUID id;
    String word;
    String meaning;
}
