package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.SubDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubDetailRepository extends JpaRepository<SubDetail, UUID> {

    // Lấy danh sách chi tiết đáp án đã chọn theo lượt thi (Submission)
    List<SubDetail> findBySubmissionId(UUID submissionId);
}