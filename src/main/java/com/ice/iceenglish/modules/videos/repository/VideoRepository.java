package com.ice.iceenglish.modules.videos.repository;

import com.ice.iceenglish.modules.videos.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    // Lấy danh sách các video bài giảng dựa theo tập hợp danh sách UUID truyền vào
    List<Video> findByIdIn(Set<UUID> ids);

    Optional<Video> findByPosition(String position);

    @Query("""
            select v from Video v
            order by case when v.positionChapter is null then 1 else 0 end,
                     v.positionChapter, v.positionLesson, v.createdAt, v.id
            """)
    List<Video> findAllInDisplayOrder();

    @Query("""
            select v from Video v
            where locate(lower(:title), lower(v.title)) > 0
            order by case when v.positionChapter is null then 1 else 0 end,
                     v.positionChapter, v.positionLesson, v.createdAt, v.id
            """)
    List<Video> findByTitleContainingIgnoreCaseInDisplayOrder(@Param("title") String title);

    @Query("select v.id from Video v where v.guestAccessible = true")
    List<UUID> findGuestAccessibleIds();
}