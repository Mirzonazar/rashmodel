package org.example.rashmodel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppUser {
    @Id
    private Long id; // Telegram Chat ID

    private String firstName;
    private String username;

    private Boolean hasAccess = false;

    // Yangi maydonlar – to‘lov muddatini boshqarish uchun
    private LocalDateTime accessGrantedAt;     // to‘lov qilingan sana
    private LocalDateTime accessExpiresAt;     // muddat tugashi (masalan +30 kun)

    // yordamchi metod (qulaylik uchun)
    public boolean isAccessActive() {
        if (!Boolean.TRUE.equals(hasAccess)) {
            return false;
        }
        if (accessExpiresAt == null) {
            return true; // eski foydalanuvchilar uchun muddatsiz (orqaga qarab moslash)
        }
        return LocalDateTime.now().isBefore(accessExpiresAt);
    }
}