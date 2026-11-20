package com.ice.iceenglish.modules.videos.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleRequest;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleResponse;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import com.ice.iceenglish.modules.videos.dto.VideoDocumentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoGuestTitleResponse;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.dto.VideoTitleResponse;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.enums.VideoType;
import com.ice.iceenglish.modules.videos.exception.VideoPositionException;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import com.ice.iceenglish.modules.submissions.repository.PartTimeLogRepository;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.ice.iceenglish.core.cache.CacheRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class VideoService {

    private static final Pattern POSITION_PATTERN = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");
    private static final String INVALID_POSITION_MESSAGE =
            "Vị trí không hợp lệ. Ví dụ: 1, 1.1, 2.10.";

    private final VideoRepository videoRepository;
    private final ExamRepository examRepository;
    private final ExamPartRepository examPartRepository;
    private final QuestionRepository questionRepository;
    private final VocabularyRepository vocabularyRepository;
    private final SubmissionRepository submissionRepository;
    private final SubDetailRepository subDetailRepository;
    private final PartTimeLogRepository partTimeLogRepository;
    private final ObjectMapper objectMapper;
    private final CacheRegistry cacheRegistry;

    // Nội dung video/homework/từ vựng chỉ đổi khi giáo viên tạo bundle hoặc đổi trạng thái đề; xem CacheRegistry.
    private final Cache<UUID, VideoLearningContentResponse> learningContentCache;
    private final Cache<UUID, VideoDocumentResponse> documentCache;
    private final Cache<UUID, ExamResponseDto> homeworkCache;
    private final Cache<UUID, HomeworkAnswersResponse> homeworkAnswersCache;
    private final Cache<UUID, List<VocabularyResponseDto>> vocabulariesCache;
    private final Cache<String, List<VideoResponseDto>> videoListCache;
    private final Cache<String, List<VideoTitleResponse>> videoTitlesCache;
    private final Cache<String, List<VideoGuestTitleResponse>> guestVideoTitlesCache;

    public VideoService(VideoRepository videoRepository, ExamRepository examRepository,
                        ExamPartRepository examPartRepository, QuestionRepository questionRepository,
                        VocabularyRepository vocabularyRepository, SubmissionRepository submissionRepository,
                        SubDetailRepository subDetailRepository, PartTimeLogRepository partTimeLogRepository,
                        ObjectMapper objectMapper, CacheRegistry cacheRegistry) {
        this.videoRepository = videoRepository;
        this.examRepository = examRepository;
        this.examPartRepository = examPartRepository;
        this.questionRepository = questionRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.submissionRepository = submissionRepository;
        this.subDetailRepository = subDetailRepository;
        this.partTimeLogRepository = partTimeLogRepository;
        this.objectMapper = objectMapper;
        this.cacheRegistry = cacheRegistry;
        this.learningContentCache = cacheRegistry.create(1_000);
        this.documentCache = cacheRegistry.create(1_000);
        this.homeworkCache = cacheRegistry.create(1_000);
        this.homeworkAnswersCache = cacheRegistry.create(1_000);
        this.vocabulariesCache = cacheRegistry.create(1_000);
        this.videoListCache = cacheRegistry.create(4);
        this.videoTitlesCache = cacheRegistry.create(4);
        this.guestVideoTitlesCache = cacheRegistry.create(4);
    }

    @Transactional
    public CreateVideoLearningBundleResponse createLearningBundle(
            MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw validation("file: bắt buộc và không được rỗng.");
        }
        CreateVideoLearningBundleRequest request;
        try (InputStream in = file.getInputStream()) {
            request = objectMapper.readValue(in, CreateVideoLearningBundleRequest.class);
        } catch (JsonProcessingException e) {
            JsonLocation loc = e.getLocation();
            String where = loc != null
                    ? " (dòng " + loc.getLineNr() + ", cột " + loc.getColumnNr() + ")" : "";
            throw validation("JSON không hợp lệ" + where + ": " + e.getOriginalMessage());
        } catch (IOException e) {
            throw validation("Không đọc được file upload: " + e.getMessage());
        }
        return createLearningBundle(request, createdBy);
    }

    @Transactional
    public CreateVideoLearningBundleResponse createLearningBundle(
            CreateVideoLearningBundleRequest request, String createdBy) {
        VideoType videoType = validateRequest(request);

        CreateVideoLearningBundleRequest.VideoInfo videoInfo = request.getVideo();
        ParsedPosition position = parsePosition(videoInfo.getPosition());
        ensurePositionAvailable(position, null);
        Video video = videoRepository.save(Video.builder()
                .title(videoInfo.getTitle().trim())
                .videoType(videoType)
                .position(position.value())
                .positionChapter(position.chapter())
                .positionLesson(position.lesson())
                .videoUrl(videoInfo.getVideoUrl().trim())
                .documentUrl(blankToNull(videoInfo.getDocumentUrl()))
                .build());
        flushPositionOrConflict(position.value());

        Exam savedHomework = request.getHomework() == null ? null : examRepository.save(toHomework(
                request.getHomework(), video, createdBy, videoInfo.getAnswersUrl()));
        List<CreateVideoLearningBundleRequest.VocabularyInfo> vocabularyItems =
                request.getVocabularies() == null ? List.of() : request.getVocabularies();
        List<Vocabulary> vocabularies = vocabularyItems.stream()
                .map(item -> Vocabulary.builder()
                        .video(video)
                        .word(item.getWord().trim())
                        .meaning(item.getMeaning().trim())
                        .phonetic(emptyToNull(item.getPhonetic()))
                        .exampleSentence(emptyToNull(item.getExampleSentence()))
                        .exampleSentenceTranslate(emptyToNull(item.getExampleSentenceTranslate()))
                        .build())
                .toList();

        if (!vocabularies.isEmpty()) {
            vocabularyRepository.saveAll(vocabularies);
        }
        cacheRegistry.evictAllAfterCommit();

        return CreateVideoLearningBundleResponse.builder()
                .videoId(video.getId())
                .homeworkId(savedHomework == null ? null : savedHomework.getId())
                .vocabularyCount(vocabularies.size())
                .build();
    }

    private Exam toHomework(CreateVideoLearningBundleRequest.HomeworkInfo request,
                            Video video, String createdBy, String videoAnswersUrl) {
        if (request == null || isBlank(request.getTitle())) {
            throw validation("homework.title: bắt buộc.");
        }
        Exam homework = Exam.builder()
                .title(request.getTitle().trim())
                .examType(ExamType.HOMEWORK)
                .status(ExamStatus.PUBLISHED)
                .answersUrl(blankToNull(!isBlank(request.getAnswersUrl())
                        ? request.getAnswersUrl() : videoAnswersUrl))
                .createdBy(createdBy)
                .video(video)
                .build();

        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw validation("homework.questions: phải có ít nhất 1 câu.");
        }
        for (CreateVideoLearningBundleRequest.QuestionInfo questionInfo : request.getQuestions()) {
            if (questionInfo == null || isBlank(questionInfo.getContent())
                    || questionInfo.getOptions() == null
                    || isBlank(questionInfo.getCorrectOption())
                    || questionInfo.getOrderIndex() == null) {
                throw validation("homework.questions: thiếu trường bắt buộc.");
            }
            String correctOpt = questionInfo.getCorrectOption().trim().toUpperCase();
            if (!List.of("A", "B", "C", "D").contains(correctOpt)) {
                throw validation("homework.questions.correctOption: chỉ nhận A, B, C hoặc D.");
            }
            Map<String, String> options = questionInfo.getOptions();
            if (List.of("A", "B", "C", "D").stream().anyMatch(key -> isBlank(options.get(key)))) {
                throw validation("homework.questions.options: phải có đủ A, B, C và D.");
            }
            homework.addQuestion(Question.builder()
                    .content(questionInfo.getContent().trim())
                    .optA(options.get("A").trim())
                    .optB(options.get("B").trim())
                    .optC(options.get("C").trim())
                    .optD(options.get("D").trim())
                    .correctOpt(correctOpt)
                    .explanation(blankToNull(questionInfo.getExplanation()))
                    .relatedVideo(video)
                    .orderIndex(questionInfo.getOrderIndex())
                    .build());
        }
        return homework;
    }

    private VideoType validateRequest(CreateVideoLearningBundleRequest request) {
        if (request == null || request.getVideo() == null) {
            throw validation("video: bắt buộc.");
        }
        CreateVideoLearningBundleRequest.VideoInfo video = request.getVideo();
        if (isBlank(video.getTitle()) || isBlank(video.getVideoUrl())) {
            throw validation("video.title và video.videoUrl: bắt buộc.");
        }
        parsePosition(video.getPosition());
        VideoType videoType = parseVideoType(video.getType());
        List<CreateVideoLearningBundleRequest.VocabularyInfo> vocabularies =
                request.getVocabularies() == null ? List.of() : request.getVocabularies();
        if (videoType == VideoType.LECTURE
                && (request.getHomework() == null || vocabularies.isEmpty())) {
            throw validation("video.type=lecture yêu cầu homework và ít nhất 1 từ vựng.");
        }
        for (int i = 0; i < vocabularies.size(); i++) {
            CreateVideoLearningBundleRequest.VocabularyInfo item = vocabularies.get(i);
            if (item == null || isBlank(item.getWord()) || isBlank(item.getMeaning())) {
                throw validation("vocabularies: word và meaning là bắt buộc.");
            }
            String path = "vocabularies[" + i + "].";
            validateLength(item.getPhonetic(), 250, path + "phonetic");
            validateLength(item.getExampleSentence(), 5000, path + "example_sentence");
            validateLength(item.getExampleSentenceTranslate(), 5000, path + "example_sentence_translate");
            if (!isBlank(item.getExampleSentenceTranslate()) && isBlank(item.getExampleSentence())) {
                throw validation(path + "example_sentence_translate yêu cầu example_sentence.");
            }
        }
        return videoType;
    }

    private VideoType parseVideoType(String type) {
        if (type == null) {
            return VideoType.LECTURE;
        }
        try {
            return VideoType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validation("video.type: chỉ nhận lecture, practice hoặc exam_tips.");
        }
    }

    private static void validateLength(String value, int max, String field) {
        if (value != null && value.codePointCount(0, value.length()) > max) {
            throw validation(field + ": tối đa " + max + " ký tự.");
        }
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static ResponseStatusException validation(String message) {
        return new ResponseStatusException(BAD_REQUEST, message);
    }

    public VideoLearningContentResponse getLearningContent(UUID videoId) {
        return cacheRegistry.getOrLoad(learningContentCache, videoId, () -> {
            Video video = findVideo(videoId);
            ExamResponseDto homework = findHomework(videoId);
            return VideoLearningContentResponse.builder()
                    .videoId(video.getId())
                    .title(video.getTitle())
                    .videoUrl(video.getVideoUrl())
                    .documentUrl(video.getDocumentUrl())
                    .homework(homework)
                    .vocabularies(loadVocabularies(videoId))
                    .build();
        });
    }

    public VideoDocumentResponse getDocument(UUID videoId) {
        return cacheRegistry.getOrLoad(documentCache, videoId, () -> {
            Video video = findVideo(videoId);
            return VideoDocumentResponse.builder()
                    .videoId(video.getId())
                    .documentUrl(video.getDocumentUrl())
                    .build();
        });
    }

    public ExamResponseDto getHomework(UUID videoId) {
        return cacheRegistry.getOrLoad(homeworkCache, videoId, () -> {
            findVideo(videoId);
            ExamResponseDto homework = findHomework(videoId);
            if (homework == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy homework của video: " + videoId);
            }
            return homework;
        });
    }

    public HomeworkAnswersResponse getHomeworkAnswers(UUID videoId) {
        return cacheRegistry.getOrLoad(homeworkAnswersCache, videoId, () -> {
            findVideo(videoId);
            Exam homework = findHomeworkEntity(videoId);
            if (homework == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy homework của video: " + videoId);
            }
            return HomeworkAnswersResponse.builder()
                    .videoId(videoId)
                    .homeworkId(homework.getId())
                    .answersUrl(homework.getAnswersUrl())
                    .build();
        });
    }

    public List<VocabularyResponseDto> getVocabularies(UUID videoId) {
        return cacheRegistry.getOrLoad(vocabulariesCache, videoId, () -> loadVocabularies(videoId));
    }

    public List<VideoGuestTitleResponse> getAllGuestTitles() {
        return cacheRegistry.getOrLoad(guestVideoTitlesCache, "ALL", () ->
                videoRepository.findAllInDisplayOrder().stream()
                        .map(video -> new VideoGuestTitleResponse(
                                video.getId(), video.getTitle(), video.getPosition(),
                                video.isGuestAccessible()))
                        .toList());
    }

    public void requireGuestAccess(UUID videoId) {
        Video video = findVideo(videoId);
        if (!video.isGuestAccessible()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Video này dành cho học viên ICE English.");
        }
    }

    public Set<UUID> getGuestAccessibleVideoIds() {
        return Set.copyOf(videoRepository.findGuestAccessibleIds());
    }

    @Transactional
    public void guestAccess(UUID videoId, Boolean guestAccessible) {
        if (guestAccessible == null) {
            throw validation("guestAccessible là bắt buộc.");
        }
        findVideo(videoId).setGuestAccessible(guestAccessible);
        cacheRegistry.evictAllAfterCommit();
    }

    @Transactional
    public void updatePosition(UUID videoId, String requestedPosition) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoPositionException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy video: " + videoId));
        ParsedPosition position = parsePosition(requestedPosition);
        ensurePositionAvailable(position, videoId);
        video.setPosition(position.value());
        video.setPositionChapter(position.chapter());
        video.setPositionLesson(position.lesson());
        flushPositionOrConflict(position.value());
        cacheRegistry.evictAllAfterCommit();
    }

    @Transactional
    public void delete(UUID videoId) {
        Video video = findVideo(videoId);
        List<Exam> linkedExams = examRepository.findByVideoIdWithParts(videoId);
        List<UUID> linkedExamIds = linkedExams.stream().map(Exam::getId).toList();

        if (!linkedExamIds.isEmpty()) {
            subDetailRepository.deleteBySubmissionExamIds(linkedExamIds);
            partTimeLogRepository.deleteBySubmissionExamIds(linkedExamIds);
            submissionRepository.deleteByExamIds(linkedExamIds);
        }

        questionRepository.clearRelatedVideoReference(videoId);
        vocabularyRepository.deleteByVideoId(videoId);
        examRepository.clearVideoReference(videoId);
        examRepository.deleteAll(linkedExams);
        videoRepository.delete(video);
        cacheRegistry.evictAllAfterCommit();
    }

    private void flushPositionOrConflict(String position) {
        if (position == null) {
            return;
        }
        try {
            videoRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw positionConflict(position, null, exception);
        }
    }

    private void ensurePositionAvailable(ParsedPosition position, UUID currentVideoId) {
        if (position.value() == null) {
            return;
        }
        videoRepository.findByPosition(position.value()).ifPresent(existing -> {
            if (currentVideoId == null || !currentVideoId.equals(existing.getId())) {
                throw positionConflict(position.value(), existing.getTitle(), null);
            }
        });
    }

    private static VideoPositionException positionConflict(
            String position, String existingTitle, Throwable cause) {
        String message = "Vị trí " + position + " đã được dùng cho video khác";
        if (!isBlank(existingTitle)) {
            message += ": " + existingTitle;
        }
        return new VideoPositionException(HttpStatus.CONFLICT, message + ".", cause);
    }

    private static ParsedPosition parsePosition(String requestedPosition) {
        if (requestedPosition == null || requestedPosition.isBlank()) {
            return new ParsedPosition(null, null, null);
        }
        String position = requestedPosition.trim();
        if (!POSITION_PATTERN.matcher(position).matches()) {
            throw new VideoPositionException(BAD_REQUEST, INVALID_POSITION_MESSAGE);
        }
        String[] parts = position.split("\\.", -1);
        try {
            int chapter = Integer.parseInt(parts[0]);
            int lesson = parts.length == 1 ? 0 : Integer.parseInt(parts[1]);
            return new ParsedPosition(position, chapter, lesson);
        } catch (NumberFormatException exception) {
            throw new VideoPositionException(BAD_REQUEST, INVALID_POSITION_MESSAGE);
        }
    }

    private record ParsedPosition(String value, Integer chapter, Integer lesson) {
    }

    private List<VocabularyResponseDto> loadVocabularies(UUID videoId) {
        findVideo(videoId);
        return vocabularyRepository.findByVideoIdOrderByCreatedAtAsc(videoId).stream()
                .map(item -> VocabularyResponseDto.builder()
                        .id(item.getId())
                        .word(item.getWord())
                        .meaning(item.getMeaning())
                        .phonetic(item.getPhonetic())
                        .exampleSentence(item.getExampleSentence())
                        .exampleSentenceTranslate(item.getExampleSentenceTranslate())
                        .build())
                .toList();
    }

    private Video findVideo(UUID videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy video: " + videoId));
    }

    private Exam findHomeworkEntity(UUID videoId) {
        return examRepository.findByVideoIdAndExamTypeWithParts(videoId, ExamType.HOMEWORK)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ExamResponseDto findHomework(UUID videoId) {
        Exam homework = findHomeworkEntity(videoId);
        return homework == null ? null : toStudentDto(homework);
    }

    private ExamResponseDto toStudentDto(Exam exam) {
        return ExamResponseDto.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .videoId(exam.getVideo() != null ? exam.getVideo().getId() : null)
                .durationMinutes(exam.getDurationMinutes())
                .answersRevealed(exam.isAnswersRevealed())
                .answersUrl(exam.getAnswersUrl())
                .questions(exam.getQuestions().stream().map(question ->
                        ExamResponseDto.QuestionDto.builder()
                                .id(question.getId())
                                .orderIndex(question.getOrderIndex())
                                .content(question.getContent())
                                .optA(question.getOptA())
                                .optB(question.getOptB())
                                .optC(question.getOptC())
                                .optD(question.getOptD())
                                .build())
                        .toList())
                .parts(exam.getParts().stream().map(part -> ExamResponseDto.ExamPartDto.builder()
                        .id(part.getId())
                        .partName(part.getPartName())
                        .partType(part.getPartType())
                        .passageText(part.getPassageText())
                        .recommendedTimeSeconds(part.getRecommendedTimeSeconds())
                        .orderIndex(part.getOrderIndex())
                        .questions(part.getQuestions().stream().map(question ->
                                ExamResponseDto.QuestionDto.builder()
                                        .id(question.getId())
                                        .orderIndex(question.getOrderIndex())
                                        .content(question.getContent())
                                        .optA(question.getOptA())
                                        .optB(question.getOptB())
                                        .optC(question.getOptC())
                                        .optD(question.getOptD())
                                        .build())
                                .toList())
                        .build())
                        .toList())
                .build();
    }

    public List<VideoResponseDto> getAll(String title) {
        if (title == null || title.isBlank()) {
            return cacheRegistry.getOrLoad(videoListCache, "ALL",
                    () -> videoRepository.findAllInDisplayOrder().stream()
                            .map(VideoService::toDto).toList());
        }
        return findByTitleQuery(title);
    }

    @Transactional(readOnly = true)
    public List<VideoResponseDto> findByTitle(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return findByTitleQuery(title);
    }

    private List<VideoResponseDto> findByTitleQuery(String title) {
        return videoRepository.findByTitleContainingIgnoreCaseInDisplayOrder(title.trim())
                .stream()
                .map(VideoService::toDto)
                .toList();
    }

    public List<VideoTitleResponse> getAllTitles() {
        return cacheRegistry.getOrLoad(videoTitlesCache, "ALL", () ->
                videoRepository.findAllInDisplayOrder().stream()
                        .map(video -> VideoTitleResponse.builder()
                                .videoId(video.getId())
                                .title(video.getTitle())
                                .position(video.getPosition())
                                .build())
                        .toList());
    }

    private static VideoResponseDto toDto(Video video) {
        return VideoResponseDto.builder()
                .id(video.getId())
                .title(video.getTitle())
                .position(video.getPosition())
                .videoUrl(video.getVideoUrl())
                .documentUrl(video.getDocumentUrl())
                .createdAt(video.getCreatedAt())
                .build();
    }
}
