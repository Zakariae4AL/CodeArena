package dev.codearena.submissions;

public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    OUTPUT_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR,
    SYSTEM_ERROR;

    /** Compilation and system errors never cost penalty time in a contest. */
    public boolean countsAsAttempt() {
        return this != COMPILATION_ERROR && this != SYSTEM_ERROR;
    }
}
