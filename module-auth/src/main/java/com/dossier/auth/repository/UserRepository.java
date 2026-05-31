package com.dossier.auth.repository;

import com.dossier.auth.domain.AuthProvider;
import com.dossier.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email); // <=> SQL: SELECT * FROM users WHERE email = ?
    boolean existsByEmail(String email);      // <=> SQL: SELECT COUNT(*) > 0 FROM users WHERE email = ?

    // OAuth2 login: tìm user theo provider + sub claim
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId); // <=> SQL: SELECT * FROM users WHERE provider = ? AND provider_id = ?
}
