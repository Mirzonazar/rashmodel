package org.example.rashmodel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_answers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_test_id")
    private UserTest userTest;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    private String givenAnswer;
    private String subStep; // "main", "main_a", "main_b"
    private Boolean correct;
}