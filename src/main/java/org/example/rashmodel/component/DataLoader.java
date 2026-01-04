package org.example.rashmodel.component;

import org.example.rashmodel.entity.Question;
import org.example.rashmodel.repository.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private QuestionRepository questionRepository;

    @Override
    public void run(String... args) {
        if (questionRepository.count() > 0) {
            System.out.println("Savollar allaqachon yuklangan.");
            return;
        }

        List<Question> questions = new ArrayList<>();

        // 1-32 variantli savollar (to'g'ri javoblar keyinroq real qilinadi)
        for (long i = 1; i <= 32; i++) {
            double diff = i <= 10 ? -1.0 : i <= 20 ? 0.0 : 0.8;
            questions.add(createQuestion(i, "1_32", "A", false, false, diff)); // Real javobni o'zgartiring
        }

        // 33-35 matching
        questions.add(createQuestion(33L, "33_35", "C", true, false, 1.2));
        questions.add(createQuestion(34L, "33_35", "B", true, false, 1.4));
        questions.add(createQuestion(35L, "33_35", "E", true, false, 1.6));

        // 36-45 ochiq (a va b)
        for (long i = 36; i <= 45; i++) {
            double diff = 1.8 + (i - 36) * 0.1;
            Question q = createQuestion(i, "36_45", null, false, true, diff);
            q.setCorrectAnswerA("12"); // Real javob
            q.setCorrectAnswerB("8");  // Real javob
            questions.add(q);
        }

        questionRepository.saveAll(questions);
        System.out.println("45 ta savol yuklandi.");
    }

    private Question createQuestion(Long id, String group, String correct, boolean block, boolean doubleAns, double diff) {
        Question q = new Question();
        q.setId(id);
        q.setSubject("MATEMATIKA");
        q.setQuestionGroup(group);
        q.setCorrectOption(correct);
        q.setIsBlockQuestion(block);
        q.setIsDoubleAnswer(doubleAns);
        q.setDifficulty(diff);
        q.setImagePath(id + ".png");
        return q;
    }
}