package dev.codearena.problems.internal;

import dev.codearena.problems.Difficulty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 100_000)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TestCase> tests = new ArrayList<>();

    protected Problem() {
    }

    public Problem(String slug, Instant createdAt) {
        this.slug = slug;
        this.createdAt = createdAt;
    }

    public void update(String slug, String title, String statement, Difficulty difficulty,
                       int timeLimitMs, int memoryLimitMb, boolean published) {
        this.slug = slug;
        this.title = title;
        this.statement = statement;
        this.difficulty = difficulty;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.published = published;
    }

    public void replaceTests(List<TestCase> newTests) {
        tests.clear();
        for (TestCase test : newTests) {
            test.attachTo(this);
            tests.add(test);
        }
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getStatement() {
        return statement;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getTimeLimitMs() {
        return timeLimitMs;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TestCase> getTests() {
        return tests;
    }
}
