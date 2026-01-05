package org.example.rashmodel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppUser {
    @Id
    private Long id; // Telegram Chat ID
    private String firstName;
    private String username;
    private Boolean hasAccess = false; // To'lov qilganmi?
}