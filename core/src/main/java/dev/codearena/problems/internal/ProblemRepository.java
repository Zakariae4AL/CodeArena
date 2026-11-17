package dev.codearena.problems.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Problem> findAllByPublishedTrueOrderByIdAsc();

    List<Problem> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "tests")
    Optional<Problem> findWithTestsById(Long id);
}
