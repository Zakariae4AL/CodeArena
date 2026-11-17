package dev.codearena.contests.internal;

import dev.codearena.contests.ContestDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ContestDirectoryImpl implements ContestDirectory {

    private final ContestRepository contests;

    ContestDirectoryImpl(ContestRepository contests) {
        this.contests = contests;
    }

    static ContestInfo info(Contest c) {
        return new ContestInfo(c.getId(), c.getSlug(), c.getTitle(), c.getStartsAt(), c.getEndsAt());
    }

    @Override
    public Optional<ContestInfo> byId(long contestId) {
        return contests.findById(contestId).map(ContestDirectoryImpl::info);
    }

    @Override
    public Optional<ContestInfo> bySlug(String slug) {
        return contests.findBySlug(slug).map(ContestDirectoryImpl::info);
    }

    @Override
    public List<ContestProblem> problems(long contestId) {
        return contests.findById(contestId)
                .map(c -> c.getEntries().stream().map(e -> new ContestProblem(e.getProblemId(), e.getLabel())).toList())
                .orElse(List.of());
    }
}
