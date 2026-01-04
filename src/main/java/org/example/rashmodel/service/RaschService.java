package org.example.rashmodel.service;

import org.springframework.stereotype.Service;

@Service
public class RaschService {

    // Foydalanuvchi qobiliyatini (Theta) yangilash: Maximum Likelihood Estimation (MLE) soddalashtirilgan varianti
    public double updateAbility(double currentTheta, double difficulty, boolean isCorrect) {
        double probability = calculateProbability(currentTheta, difficulty);

        // Step size (o'zgarish qadami) - adaptivlikni ta'minlaydi
        double adjustment = 0.5;

        if (isCorrect) {
            currentTheta += adjustment * (1 - probability);
        } else {
            currentTheta -= adjustment * probability;
        }

        // Chegaralarni belgilash (Theta odatda -3 va +3 oralig'ida bo'ladi)
        return Math.max(-4.0, Math.min(4.0, currentTheta));
    }

    public double calculateProbability(double theta, double difficulty) {
        // Rasch modeli formulasi: P(x=1) = exp(theta - difficulty) / (1 + exp(theta - difficulty))
        return 1.0 / (1.0 + Math.exp(-(theta - difficulty)));
    }

    public String getLevel(double theta) {
        if (theta >= 2.5) return "C1 (Professional)";
        if (theta >= 1.2) return "B2 (Yuqori o'rta)";
        if (theta >= 0.0) return "B1 (O'rta)";
        if (theta >= -1.5) return "A2 (Past o'rta)";
        return "A1 (Boshlang'ich)";
    }
}