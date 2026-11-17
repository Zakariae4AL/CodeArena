package dev.codearena.contests.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContestEntry {

    @Column(name = "problem_id", nullable = false)
    private long problemId;

    @Column(nullable = false, length = 2)
    private String label;

    @Column(nullable = false)
    private int position;

    protected ContestEntry() {
    }

    public ContestEntry(long problemId, String label, int position) {
        this.problemId = problemId;
        this.label = label;
        this.position = position;
    }

    public long getProblemId() {
        return problemId;
    }

    public String getLabel() {
        return label;
    }

    public int getPosition() {
        return position;
    }
}
