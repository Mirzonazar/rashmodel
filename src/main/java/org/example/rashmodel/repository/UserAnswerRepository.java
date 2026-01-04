package org.example.rashmodel.repository;

import org.example.rashmodel.entity.Question;
import org.example.rashmodel.entity.UserAnswer;
import org.example.rashmodel.entity.UserTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {
}