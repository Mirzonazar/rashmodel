package org.example.rashmodel.repository;

import org.example.rashmodel.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
}