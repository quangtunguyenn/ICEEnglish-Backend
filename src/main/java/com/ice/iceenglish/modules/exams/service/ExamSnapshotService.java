package com.ice.iceenglish.modules.exams.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cấp {@link ExamSnapshot} từ cache; khi chưa có thì nạp từ DB (trong transaction của người gọi).
 * Nội dung đề không đổi sau khi tạo, riêng trạng thái (publish/archive) được làm mới nhờ
 * {@link CacheRegistry#evictAllAfterCommit()}.
 */
@Service
public class ExamSnapshotService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final Cache<UUID, ExamSnapshot> cache;

    public ExamSnapshotService(ExamRepository examRepository, QuestionRepository questionRepository,
                               CacheRegistry cacheRegistry) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.cache = cacheRegistry.create(500);
    }

    public Optional<ExamSnapshot> find(UUID examId) {
        // Caffeine chỉ cho một luồng nạp cho mỗi key, tránh hàng trăm luồng cùng nạp một đề khi cache trống.
        return Optional.ofNullable(cache.get(examId, this::load));
    }

    private ExamSnapshot load(UUID examId) {
        Exam exam = examRepository.findByIdWithParts(examId).orElse(null);
        if (exam == null) {
            return null;
        }
        List<ExamSnapshot.PartSnap> parts = exam.getParts().stream()
                .map(part -> new ExamSnapshot.PartSnap(part.getId(), part.getPartName(), part.getPartType(),
                        part.getPassageText(), part.getTranslate(), part.getOrderIndex(),
                        part.isFixedPosition(), part.getShuffleGroup()))
                .toList();
        List<ExamSnapshot.QuestionSnap> questions = questionRepository.findAllWithDetailsByExamId(examId)
                .stream()
                .map(ExamSnapshotService::toSnap)
                .toList();
        return new ExamSnapshot(exam.getId(), exam.getTitle(), exam.getExamType(), exam.getStatus(),
                exam.getDurationMinutes(), exam.isShuffleParts(), parts, questions);
    }

    private static ExamSnapshot.QuestionSnap toSnap(Question q) {
        ExamPart part = q.getPart();
        Video video = q.getRelatedVideo();
        return new ExamSnapshot.QuestionSnap(q.getId(), q.getOrderIndex(), q.getContent(),
                q.getOptA(), q.getOptB(), q.getOptC(), q.getOptD(), q.getCorrectOpt(),
                q.getExplanation(), part == null ? null : part.getId(), part == null ? null : part.getPartType(),
                video == null ? null : new ExamSnapshot.VideoSnap(video.getId(), video.getTitle(), video.getVideoUrl()));
    }
}
