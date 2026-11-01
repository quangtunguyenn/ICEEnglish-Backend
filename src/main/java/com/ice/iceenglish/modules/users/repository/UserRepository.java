package com.ice.iceenglish.modules.users.repository;


import com.ice.iceenglish.modules.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    // Tự động sinh câu lệnh: SELECT count(*) FROM users WHERE username = ?
    boolean existsByUsername(String username);
    
    // Tự động sinh câu lệnh: SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);
}