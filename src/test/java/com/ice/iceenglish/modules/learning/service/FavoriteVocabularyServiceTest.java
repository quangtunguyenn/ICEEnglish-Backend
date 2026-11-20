package com.ice.iceenglish.modules.learning.service;

import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyRequest;
import com.ice.iceenglish.modules.learning.entity.FavoriteVocabulary;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.FavoriteVocabularyRepository;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteVocabularyServiceTest {

    private final FavoriteVocabularyRepository favorites = mock(FavoriteVocabularyRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private FavoriteVocabularyService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteVocabularyService(favorites, vocabularies, users);
    }

    @Test
    void addsAUserSuppliedWordWithoutLookingItUpInTheVocabularyBank() {
        UUID favoriteId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).username("student").build();
        FavoriteVocabulary saved = FavoriteVocabulary.builder()
                .id(favoriteId)
                .user(user)
                .word("quokka")
                .meaning("một loài thú có túi")
                .build();
        when(favorites.findByUserUsernameAndWordIgnoreCaseAndMeaningIgnoreCase(
                "student", "quokka", "một loài thú có túi")).thenReturn(Optional.empty());
        when(users.findByUsername("student")).thenReturn(Optional.of(user));
        when(favorites.save(any(FavoriteVocabulary.class))).thenReturn(saved);

        var response = service.addFavorite(
                "student", new FavoriteVocabularyRequest(" quokka ", " một loài thú có túi "));

        assertEquals(favoriteId, response.getId());
        assertEquals("quokka", response.getWord());
        assertEquals("một loài thú có túi", response.getMeaning());
        ArgumentCaptor<FavoriteVocabulary> savedFavorite = ArgumentCaptor.forClass(FavoriteVocabulary.class);
        verify(favorites).save(savedFavorite.capture());
        assertEquals("quokka", savedFavorite.getValue().getWord());
        assertEquals("một loài thú có túi", savedFavorite.getValue().getMeaning());
        verifyNoInteractions(vocabularies);
    }

    @Test
    void reusesExistingFavoriteAndRejectsEmptyValues() {
        FavoriteVocabulary existing = FavoriteVocabulary.builder()
                .id(UUID.randomUUID())
                .user(User.builder().username("student").build())
                .word("apple")
                .meaning("quả táo")
                .build();
        when(favorites.findByUserUsernameAndWordIgnoreCaseAndMeaningIgnoreCase("student", "apple", "quả táo"))
                .thenReturn(Optional.of(existing));

        var response = service.addFavorite("student", new FavoriteVocabularyRequest("apple", "quả táo"));

        assertEquals(existing.getId(), response.getId());
        verify(favorites, never()).save(any());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addFavorite("student", new FavoriteVocabularyRequest(" ", "meaning")));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void returnsRandomVocabularyBankForMultipleChoiceOptions() {
        Vocabulary vocabulary = Vocabulary.builder()
                .id(UUID.randomUUID())
                .word("apple")
                .meaning("quả táo")
                .build();
        when(vocabularies.findRandomVocabularyOptions(any(PageRequest.class))).thenReturn(List.of(vocabulary));

        var response = service.getDeck("student");

        assertEquals(1, response.getQuizOptions().size());
        assertEquals(vocabulary.getId(), response.getQuizOptions().get(0).getId());
        verify(vocabularies).findRandomVocabularyOptions(PageRequest.of(0, 40));
    }
}
