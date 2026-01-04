package org.example.rashmodel.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_test")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserTest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String subject;
    private Integer currentStep = 0;
    private String currentSubStep = "a";

    private Integer correctCount = 0;
    private Double abilityScore = 0.0;
    private String level = "Aniqlanmagan";
    private Boolean isFinished = false;
    private LocalDateTime startTime;
    private LocalDateTime finishedAt;

    @NotNull
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @OneToMany(mappedBy = "userTest", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<UserAnswer> answers = new ArrayList<>();
}