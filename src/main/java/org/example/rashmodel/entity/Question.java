package org.example.rashmodel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    private Long id;

    private String subject;

    private String questionGroup;

    private String imagePath;

    private String correctOption;

    private Double difficulty;

    private Boolean isBlockQuestion = false;

    private Boolean isDoubleAnswer = false;

    private String correctAnswerA;

    private String correctAnswerB;

    public String getInternalPath() {
        String folder;
        if (id >= 1 && id <= 32) {
            folder = "1_32";
        } else if (id >= 33 && id <= 35) {
            folder = "33_35";
        } else if (id >= 36 && id <= 45) {
            folder = "36_45";
        } else {
            folder = "default";
        }
        return "static/images/MATEMATIKA/" + folder + "/" + id + ".png";
    }
}