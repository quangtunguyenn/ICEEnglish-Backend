package com.ice.iceenglish.modules.videos.repository;

import com.ice.iceenglish.modules.videos.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    // Lấy danh sách các video bài giảng dựa theo tập hợp danh sách UUID truyền vào
    List<Video> findByIdIn(Set<UUID> ids);

    List<Video> findByTitleContainingIgnoreCaseOrderByTitleAsc(String title);

    List<Video> findAllByOrderByTitleAsc();
}