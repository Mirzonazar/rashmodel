package org.example.rashmodel.repository;

import org.example.rashmodel.entity.UserTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTestRepository extends JpaRepository<UserTest, Long> {
    UserTest findByUserIdAndIsFinished(Long userId, Boolean isFinished);
}