package dev.codearena.contests.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 20_000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contest_problems", joinColumns = @JoinColumn(name = "contest_id"))
    @OrderBy("position ASC")
    private List<ContestEntry> entries = new ArrayList<>();

    protected Contest() {
    }

    public Contest(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void update(String slug, String title, String description, Instant startsAt, Instant endsAt,
                       List<Long> problemIds) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.entries.clear();
        for (int i = 0; i < problemIds.size(); i++) {
            entries.add(new ContestEntry(problemIds.get(i), String.valueOf((char) ('A' + i)), i));
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

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public List<ContestEntry> getEntries() {
        return entries;
    }
}
