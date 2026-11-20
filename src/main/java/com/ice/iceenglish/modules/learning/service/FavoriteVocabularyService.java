package com.ice.iceenglish.modules.learning.service;

import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyDeckResponse;
import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyRequest;
import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyResponse;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.learning.entity.FavoriteVocabulary;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.FavoriteVocabularyRepository;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteVocabularyService {

    private static final int QUIZ_OPTION_LIMIT = 40;

    private final FavoriteVocabularyRepository favoriteVocabularyRepository;
    private final VocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public FavoriteVocabularyDeckResponse getDeck(String username) {
        List<FavoriteVocabularyResponse> favorites = favoriteVocabularyRepository
                .findByUserUsernameOrderByCreatedAtDesc(username)
                .stream()
                .map(FavoriteVocabularyService::toFavoriteResponse)
                .toList();
        List<VocabularyResponseDto> quizOptions = vocabularyRepository
                .findRandomVocabularyOptions(PageRequest.of(0, QUIZ_OPTION_LIMIT))
                .stream()
                .map(FavoriteVocabularyService::toVocabularyResponse)
                .toList();
        return FavoriteVocabularyDeckResponse.builder()
                .favorites(favorites)
                .quizOptions(quizOptions)
                .build();
    }

    @Transactional
    public FavoriteVocabularyResponse addFavorite(String username, FavoriteVocabularyRequest request) {
        String word = request == null || request.word() == null ? "" : request.word().trim();
        String meaning = request == null || request.meaning() == null ? "" : request.meaning().trim();
        if (word.isEmpty() || meaning.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập từ và nghĩa tiếng Việt.");
        }
        if (word.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ tiếng Anh không được vượt quá 100 ký tự.");
        }

        return favoriteVocabularyRepository.findByUserUsernameAndWordIgnoreCaseAndMeaningIgnoreCase(
                        username, word, meaning)
                .map(FavoriteVocabularyService::toFavoriteResponse)
                .orElseGet(() -> {
                    var user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản đăng nhập."));
                    FavoriteVocabulary favorite = favoriteVocabularyRepository.save(FavoriteVocabulary.builder()
                            .user(user)
                            .word(word)
                            .meaning(meaning)
                            .build());
                    return toFavoriteResponse(favorite);
                });
    }

    @Transactional
    public void removeFavorite(String username, UUID favoriteId) {
        FavoriteVocabulary favorite = favoriteVocabularyRepository.findByIdAndUserUsername(favoriteId, username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy flashcard yêu thích."));
        favoriteVocabularyRepository.delete(favorite);
    }

    private static FavoriteVocabularyResponse toFavoriteResponse(FavoriteVocabulary favorite) {
        return FavoriteVocabularyResponse.builder()
                .id(favorite.getId())
                .word(favorite.getWord())
                .meaning(favorite.getMeaning())
                .build();
    }

    private static VocabularyResponseDto toVocabularyResponse(Vocabulary vocabulary) {
        return VocabularyResponseDto.builder()
                .id(vocabulary.getId())
                .word(vocabulary.getWord())
                .meaning(vocabulary.getMeaning())
                .phonetic(vocabulary.getPhonetic())
                .exampleSentence(vocabulary.getExampleSentence())
                .exampleSentenceTranslate(vocabulary.getExampleSentenceTranslate())
                .build();
    }
}
