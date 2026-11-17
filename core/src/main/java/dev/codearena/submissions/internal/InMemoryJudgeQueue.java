package dev.codearena.submissions.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps jobs in memory instead of sending them to Redis. Used by tests. */
@Component
@ConditionalOnProperty(name = "codearena.judge.queue", havingValue = "memory")
public class InMemoryJudgeQueue implements JudgeQueue {

    private final List<JudgeProtocol.Job> jobs = new CopyOnWriteArrayList<>();

    @Override
    public void send(JudgeProtocol.Job job) {
        jobs.add(job);
    }

    /** Language and mode of the jobs sent for a submission, for tests. */
    public List<String> jobsFor(String submissionId) {
        return jobs.stream().filter(j -> j.submissionId().equals(submissionId))
                .map(j -> j.language() + ":" + j.mode() + ":" + j.tests().size())
                .toList();
    }
}
