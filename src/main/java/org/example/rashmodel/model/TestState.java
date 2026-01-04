package org.example.rashmodel.model;

import lombok.Getter;
import lombok.Setter;
import org.example.rashmodel.entity.Question;
import org.example.rashmodel.entity.UserTest;

@Getter @Setter
public class TestState {
    private UserTest test;
    private Question currentQuestion;
    private int currentStep = 0;
    private String pendingSubQuestion = null; // 'a' yoki 'b'

    // BU MAYDONNI QO'SHING:
    private String tempAnswerA;

    public TestState(UserTest test) {
        this.test = test;
    }

    public void next() {
        this.currentStep++;
    }

    public boolean isLast() {
        return currentStep >= 44; // Jami 45 ta savol (0-44)
    }
}