package com.euronet.eft.portal.security.local;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LocalUserRepository extends JpaRepository<LocalUser, Long> {

    @Query("select u from LocalUser u where lower(u.username) = lower(?1)")
    Optional<LocalUser> findByUsernameIgnoreCase(String username);

    List<LocalUser> findAllByOrderByUsernameAsc();
}
