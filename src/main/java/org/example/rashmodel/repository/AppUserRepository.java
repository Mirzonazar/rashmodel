package org.example.rashmodel.repository;

import org.example.rashmodel.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    List<AppUser> findByAccessExpiresAtBefore(LocalDateTime date);
}