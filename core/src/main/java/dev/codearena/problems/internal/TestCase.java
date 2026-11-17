package dev.codearena.problems.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 2_000_000)
    private String input;

    @Column(nullable = false, length = 2_000_000)
    private String expected;

    @Column(nullable = false)
    private boolean sample;

    protected TestCase() {
    }

    public TestCase(int position, String input, String expected, boolean sample) {
        this.position = position;
        this.input = input;
        this.expected = expected;
        this.sample = sample;
    }

    void attachTo(Problem problem) {
        this.problem = problem;
    }

    public int getPosition() {
        return position;
    }

    public String getInput() {
        return input;
    }

    public String getExpected() {
        return expected;
    }

    public boolean isSample() {
        return sample;
    }
}
