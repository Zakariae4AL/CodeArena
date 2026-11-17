package dev.codearena.problems.internal;

import dev.codearena.problems.ProblemCatalog.ProblemStatement;
import dev.codearena.problems.ProblemCatalog.ProblemSummary;
import dev.codearena.shared.ApiException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public problem library: only published problems. */
@RestController
@RequestMapping("/api/problems")
class ProblemController {

    private final ProblemRepository problems;

    ProblemController(ProblemRepository problems) {
        this.problems = problems;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<ProblemSummary> list() {
        return problems.findAllByPublishedTrueOrderByIdAsc().stream().map(ProblemCatalogImpl::toSummary).toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    ProblemStatement get(@PathVariable String slug) {
        return problems.findBySlug(slug)
                .filter(Problem::isPublished)
                .map(ProblemCatalogImpl::toStatement)
                .orElseThrow(() -> ApiException.notFound("Problem not found."));
    }
}
