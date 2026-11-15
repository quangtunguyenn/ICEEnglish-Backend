package com.ice.iceenglish.modules.users.repository;


import com.ice.iceenglish.modules.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    // Tự động sinh câu lệnh: SELECT count(*) FROM users WHERE username = ?
    boolean existsByUsername(String username);
    
    // Tự động sinh câu lệnh: SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);

    boolean existsByUsernameAndIsActiveTrue(String username);

    @Query("select u from User u where upper(trim(u.role)) = 'STUDENT' order by u.createdAt desc, u.id desc")
    List<User> findAllStudents();

    @Query("select u from User u where u.id = :id and upper(trim(u.role)) = 'STUDENT'")
    Optional<User> findStudentById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
}