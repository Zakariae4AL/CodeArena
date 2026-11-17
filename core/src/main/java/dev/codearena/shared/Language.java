package dev.codearena.shared;

import java.util.Arrays;
import java.util.Optional;

/** Languages the judge can run. The ids match the judge worker's language table. */
public enum Language {
    CPP("cpp", "C++20"),
    JAVA("java", "Java 25"),
    PYTHON("python", "Python 3"),
    JAVASCRIPT("javascript", "JavaScript (Node.js)");

    private final String id;
    private final String label;

    Language(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<Language> fromId(String id) {
        return Arrays.stream(values()).filter(l -> l.id.equals(id)).findFirst();
    }
}
