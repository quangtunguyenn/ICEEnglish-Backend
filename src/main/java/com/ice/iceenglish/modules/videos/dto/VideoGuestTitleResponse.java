package com.ice.iceenglish.modules.videos.dto;

import java.util.UUID;

public record VideoGuestTitleResponse(UUID videoId, String title, String position, boolean accessible) {
}
