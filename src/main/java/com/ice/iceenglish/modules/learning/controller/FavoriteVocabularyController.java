package com.ice.iceenglish.modules.learning.controller;

import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyDeckResponse;
import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyRequest;
import com.ice.iceenglish.modules.learning.dto.FavoriteVocabularyResponse;
import com.ice.iceenglish.modules.learning.service.FavoriteVocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabularies/favorites")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('STUDENT','ROLE_STUDENT')")
public class FavoriteVocabularyController {

    private final FavoriteVocabularyService favoriteVocabularyService;

    @GetMapping
    public ResponseEntity<FavoriteVocabularyDeckResponse> getDeck(Authentication authentication) {
        return ResponseEntity.ok(favoriteVocabularyService.getDeck(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<FavoriteVocabularyResponse> addFavorite(
            @RequestBody FavoriteVocabularyRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteVocabularyService.addFavorite(authentication.getName(), request));
    }

    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable UUID favoriteId,
            Authentication authentication) {
        favoriteVocabularyService.removeFavorite(authentication.getName(), favoriteId);
        return ResponseEntity.noContent().build();
    }
}
