package org.example.rashmodel.repository;

import org.example.rashmodel.entity.UserTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTestRepository extends JpaRepository<UserTest, Long> {
    UserTest findByUserIdAndIsFinished(Long userId, Boolean isFinished);
    @Query("SELECT ut FROM UserTest ut " +
            "WHERE ut.userId = :userId AND ut.isFinished = :isFinished " +
            "ORDER BY ut.startTime DESC LIMIT 1")
    UserTest findLatestOpenTest(@Param("userId") Long userId, @Param("isFinished") Boolean isFinished);
}