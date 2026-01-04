package org.example.rashmodel.service;

import org.example.rashmodel.entity.*;
import org.example.rashmodel.repository.UserAnswerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TestService {

    @Autowired
    private UserAnswerRepository userAnswerRepository;

    public UserTest startNewTest(Long userId, String subject) {
        UserTest test = new UserTest();
        test.setUserId(userId);
        test.setSubject(subject);
        test.setStartTime(LocalDateTime.now());
        test.setCurrentStep(0);
        test.setCurrentSubStep("a");
        test.setCorrectCount(0);
        test.setAbilityScore(0.0);
        test.setLevel("Aniqlanmagan");
        test.setIsFinished(false);
        test.setProcessed(false);
        return test;
    }

    @Transactional
    public void processSingleAnswer(UserTest test, Question question, String answer) {
        saveAnswer(test, question, answer, "main");
    }

    @Transactional
    public void processDoubleAnswerPart(UserTest test, Question question, String answer, String part) {
        saveAnswer(test, question, answer, "main_" + part);
    }

    private void saveAnswer(UserTest test, Question question, String givenAnswer, String subStep) {
        UserAnswer ua = new UserAnswer();
        ua.setUserTest(test);
        ua.setQuestion(question);
        ua.setGivenAnswer(givenAnswer);
        ua.setSubStep(subStep);

        boolean isCorrect = false;

        if ("main".equals(subStep)) {
            // Variantli va matching savollar
            if (question.getCorrectOption() != null) {
                isCorrect = givenAnswer.trim().equalsIgnoreCase(question.getCorrectOption().trim());
            }
        } else if ("main_a".equals(subStep)) {
            if (question.getCorrectAnswerA() != null) {
                isCorrect = givenAnswer.trim().equalsIgnoreCase(question.getCorrectAnswerA().trim());
            }
        } else if ("main_b".equals(subStep)) {
            if (question.getCorrectAnswerB() != null) {
                isCorrect = givenAnswer.trim().equalsIgnoreCase(question.getCorrectAnswerB().trim());
            }
        }

        ua.setCorrect(isCorrect);
        userAnswerRepository.save(ua);

        if (isCorrect) {
            test.setCorrectCount(test.getCorrectCount() + 1);
        }
    }
}