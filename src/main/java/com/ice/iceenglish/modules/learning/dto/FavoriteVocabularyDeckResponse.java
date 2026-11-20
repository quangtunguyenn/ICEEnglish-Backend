package com.ice.iceenglish.modules.learning.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FavoriteVocabularyDeckResponse {
    List<FavoriteVocabularyResponse> favorites;
    List<VocabularyResponseDto> quizOptions;
}
