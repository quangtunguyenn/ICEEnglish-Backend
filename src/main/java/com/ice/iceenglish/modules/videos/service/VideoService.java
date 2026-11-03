package com.ice.iceenglish.modules.videos.service;

import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleRequest;
import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleResponse;
import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import com.ice.iceenglish.modules.learning.repository.VocabularyRepository;
import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final ExamRepository examRepository;
    private final VocabularyRepository vocabularyRepository;

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

        Exam homework = toHomework(request.getHomework(), video, createdBy);
        List<Vocabulary> vocabularies = request.getVocabularies().stream()
                .map(item -> Vocabulary.builder()
                        .video(video)
                        .word(item.getWord().trim())
                        .meaning(item.getMeaning().trim())
                        .build())
                .toList();

        Exam savedHomework = examRepository.save(homework);
        vocabularyRepository.saveAll(vocabularies);

        return CreateVideoLearningBundleResponse.builder()
                .videoId(video.getId())
                .homeworkId(savedHomework.getId())
                .vocabularyCount(vocabularies.size())
                .build();
    }

    private Exam toHomework(CreateVideoLearningBundleRequest.HomeworkInfo request,
                            Video video, String createdBy) {
        if (request == null || isBlank(request.getTitle())) {
            throw validation("homework.title: bắt buộc.");
        }
        Exam homework = Exam.builder()
                .title(request.getTitle().trim())
                .examType(ExamType.HOMEWORK)
                .status(ExamStatus.DRAFT)
                .createdBy(createdBy)
                .video(video)
                .build();

        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw validation("homework.questions: phải có ít nhất 1 câu.");
        }
        ExamPart part = ExamPart.builder()
                .partType(PartType.GRAMMAR)
                .partName("Grammar")
                .orderIndex(1)
                .build();
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
            part.addQuestion(Question.builder()
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
        homework.addPart(part);
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

    @Transactional(readOnly = true)
    public List<VideoResponseDto> getAll(String title) {
        List<Video> videos = title == null || title.isBlank()
                ? videoRepository.findAll()
                : videoRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(title.trim());
        return videos.stream().map(VideoService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoResponseDto> findByTitle(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return videoRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(title.trim())
                .stream()
                .map(VideoService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAllTitles() {
        return videoRepository.findAllTitles();
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
