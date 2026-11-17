package dev.codearena.contests.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Contest> findAllByOrderByStartsAtDesc();
}
