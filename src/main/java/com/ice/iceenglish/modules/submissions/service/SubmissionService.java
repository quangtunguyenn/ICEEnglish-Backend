package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.entity.SubDetail;
import com.ice.iceenglish.modules.submissions.entity.Submission;
import com.ice.iceenglish.modules.submissions.repository.SubDetailRepository;
import com.ice.iceenglish.modules.submissions.repository.SubmissionRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubDetailRepository subDetailRepository;
    private final QuestionRepository questionRepository;
    private final VideoRepository videoRepository;

    @Transactional
    public ExamResultResponse submitExam(SubmitExamRequest request) {
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lượt thi"));

        // 1. Lấy danh sách câu hỏi trong đề thi từ DB
        List<Question> questions = questionRepository.findByExamId(submission.getExam().getId());
        Map<UUID, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        // Map danh sách đáp án học sinh gửi lên
        Map<UUID, String> userAnswers = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        SubmitExamRequest.UserAnswerDto::getQuestionId,
                        dto -> dto.getSelectedOpt() != null ? dto.getSelectedOpt() : ""
                ));

        int totalQuestions = questions.size();
        int totalCorrect = 0;

        List<SubDetail> detailsToSave = new ArrayList<>();
        List<ExamResultResponse.QuestionResultDto> resultDetails = new ArrayList<>();

        // 2. Lấy thông tin các Video liên quan nếu có
        Set<UUID> videoIds = questions.stream()
                .map(Question::getRelatedVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Map<UUID, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));

        // 3. Tiến hành chấm điểm từng câu
        for (Question q : questions) {
            String selectedOpt = userAnswers.getOrDefault(q.getId(), null);
            boolean isCorrect = q.getCorrectOpt().equalsIgnoreCase(selectedOpt);

            if (isCorrect) {
                totalCorrect++;
            }

            // Lưu lịch sử câu trả lời vào DB
            SubDetail detail = SubDetail.builder()
                    .submission(submission)
                    .question(q)
                    .selectedOpt(selectedOpt)
                    .isCorrect(isCorrect)
                    .build();
            detailsToSave.add(detail);

            // Xử lý thông tin Video gợi ý ôn tập (chỉ cần thiết hoặc ưu tiên khi làm sai)
            ExamResultResponse.RelatedVideoDto videoDto = null;
            if (q.getRelatedVideoId() != null && videoMap.containsKey(q.getRelatedVideoId())) {
                Video v = videoMap.get(q.getRelatedVideoId());
                videoDto = ExamResultResponse.RelatedVideoDto.builder()
                        .videoId(v.getId())
                        .videoTitle(v.getTitle())
                        .videoUrl(v.getVideoUrl())
                        .build();
            }

            // Đóng gói DTO kết quả từng câu
            resultDetails.add(ExamResultResponse.QuestionResultDto.builder()
                    .questionId(q.getId())
                    .orderIndex(q.getOrderIndex())
                    .content(q.getContent())
                    .selectedOpt(selectedOpt)
                    .correctOpt(q.getCorrectOpt())
                    .isCorrect(isCorrect)
                    .explanation(q.getExplanation())
                    .relatedVideo(videoDto) // Gửi kèm video bài giảng nếu câu này có liên kết
                    .build());
        }

        // 4. Cập nhật kết quả vào bảng submissions
        double score = ((double) totalCorrect / totalQuestions) * 10.0;
        submission.setTotalQuestions(totalQuestions);
        submission.setTotalCorrect(totalCorrect);
        submission.setSubmittedAt(LocalDateTime.now());
        
        submissionRepository.save(submission);
        subDetailRepository.saveAll(detailsToSave);

        // 5. Trả về Response đầy đủ thông tin cho Frontend
        return ExamResultResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam().getId())
                .examTitle(submission.getExam().getTitle())
                .totalQuestions(totalQuestions)
                .totalCorrect(totalCorrect)
                .score(Math.round(score * 10.0) / 10.0) // Làm tròn 1 chữ số thập phân (VD: 8.5)
                .startedAt(submission.getStartedAt())
                .submittedAt(submission.getSubmittedAt())
                .details(resultDetails)
                .build();
    }
}