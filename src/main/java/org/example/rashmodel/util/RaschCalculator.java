package org.example.rashmodel.util;

import org.example.rashmodel.entity.Question;

import java.util.List;

public class RaschCalculator {

    public static double calculateAbility(List<Question> questions, List<Boolean> responses) {
        if (questions.size() != responses.size() || questions.isEmpty()) {
            return 0.0;
        }

        int n = questions.size();
        long correctCount = responses.stream().filter(Boolean::booleanValue).count();

        double theta = Math.log((correctCount + 0.5) / (n - correctCount + 0.5));
        if (Double.isNaN(theta) || Double.isInfinite(theta)) theta = 0.0;

        int maxIter = 200;
        double tol = 0.0001;

        for (int iter = 0; iter < maxIter; iter++) {
            double prev = theta;
            double score = 0.0;
            double info = 0.0;

            for (int i = 0; i < n; i++) {
                double b = questions.get(i).getDifficulty() != null ? questions.get(i).getDifficulty() : 0.0;
                double p = 1.0 / (1.0 + Math.exp(-(theta - b)));
                double r = responses.get(i) ? 1.0 : 0.0;

                score += r - p;
                info += p * (1 - p);
            }

            if (info < 1e-10) {
                return correctCount == n ? 6.0 : correctCount == 0 ? -6.0 : theta;
            }

            theta += score / info;
            theta = Math.max(-6.0, Math.min(6.0, theta));

            if (Math.abs(theta - prev) < tol) break;
        }

        return theta;
    }
}