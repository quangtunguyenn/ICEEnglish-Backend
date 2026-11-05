package com.ice.iceenglish.modules.videos.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleRequest;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleResponse;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import com.ice.iceenglish.modules.videos.dto.VideoDocumentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.dto.VideoTitleResponse;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.ice.iceenglish.core.cache.CacheRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class VideoService {

    private final VideoRepository videoRepository;
    private final ExamRepository examRepository;
    private final ExamPartRepository examPartRepository;
    private final VocabularyRepository vocabularyRepository;
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

    public VideoService(VideoRepository videoRepository, ExamRepository examRepository,
                        ExamPartRepository examPartRepository, VocabularyRepository vocabularyRepository,
                        ObjectMapper objectMapper, CacheRegistry cacheRegistry) {
        this.videoRepository = videoRepository;
        this.examRepository = examRepository;
        this.examPartRepository = examPartRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.objectMapper = objectMapper;
        this.cacheRegistry = cacheRegistry;
        this.learningContentCache = cacheRegistry.create(1_000);
        this.documentCache = cacheRegistry.create(1_000);
        this.homeworkCache = cacheRegistry.create(1_000);
        this.homeworkAnswersCache = cacheRegistry.create(1_000);
        this.vocabulariesCache = cacheRegistry.create(1_000);
        this.videoListCache = cacheRegistry.create(4);
        this.videoTitlesCache = cacheRegistry.create(4);
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
        validateRequest(request);

        CreateVideoLearningBundleRequest.VideoInfo videoInfo = request.getVideo();
        Video video = videoRepository.save(Video.builder()
                .title(videoInfo.getTitle().trim())
                .videoUrl(videoInfo.getVideoUrl().trim())
                .documentUrl(blankToNull(videoInfo.getDocumentUrl()))
                .build());

        Exam homework = toHomework(
                request.getHomework(), video, createdBy, videoInfo.getAnswersUrl());
        List<Vocabulary> vocabularies = request.getVocabularies().stream()
                .map(item -> Vocabulary.builder()
                        .video(video)
                        .word(item.getWord().trim())
                        .meaning(item.getMeaning().trim())
                        .build())
                .toList();

        Exam savedHomework = examRepository.save(homework);
        vocabularyRepository.saveAll(vocabularies);
        cacheRegistry.evictAllAfterCommit();

        return CreateVideoLearningBundleResponse.builder()
                .videoId(video.getId())
                .homeworkId(savedHomework.getId())
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

    private void validateRequest(CreateVideoLearningBundleRequest request) {
        if (request == null || request.getVideo() == null) {
            throw validation("video: bắt buộc.");
        }
        CreateVideoLearningBundleRequest.VideoInfo video = request.getVideo();
        if (isBlank(video.getTitle()) || isBlank(video.getVideoUrl())) {
            throw validation("video.title và video.videoUrl: bắt buộc.");
        }
        if (request.getHomework() == null || request.getVocabularies() == null
                || request.getVocabularies().isEmpty()) {
            throw validation("homework và vocabularies (ít nhất 1 phần tử): bắt buộc.");
        }
        for (CreateVideoLearningBundleRequest.VocabularyInfo item : request.getVocabularies()) {
            if (item == null || isBlank(item.getWord()) || isBlank(item.getMeaning())) {
                throw validation("vocabularies: word và meaning là bắt buộc.");
            }
        }
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

    private List<VocabularyResponseDto> loadVocabularies(UUID videoId) {
        findVideo(videoId);
        return vocabularyRepository.findByVideoIdOrderByCreatedAtAsc(videoId).stream()
                .map(item -> VocabularyResponseDto.builder()
                        .id(item.getId())
                        .word(item.getWord())
                        .meaning(item.getMeaning())
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
                        .recommendedTimeSeconds(part.getPartType().getStandardTimeSeconds())
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
                    () -> videoRepository.findAll().stream().map(VideoService::toDto).toList());
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
        return videoRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(title.trim())
                .stream()
                .map(VideoService::toDto)
                .toList();
    }

    public List<VideoTitleResponse> getAllTitles() {
        return cacheRegistry.getOrLoad(videoTitlesCache, "ALL", () ->
                videoRepository.findAllByOrderByTitleAsc().stream()
                        .map(video -> VideoTitleResponse.builder()
                                .videoId(video.getId())
                                .title(video.getTitle())
                                .build())
                        .toList());
    }

    private static VideoResponseDto toDto(Video video) {
        return VideoResponseDto.builder()
                .id(video.getId())
                .title(video.getTitle())
                .videoUrl(video.getVideoUrl())
                .documentUrl(video.getDocumentUrl())
                .createdAt(video.getCreatedAt())
                .build();
    }
}
