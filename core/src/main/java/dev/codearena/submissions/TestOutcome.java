package dev.codearena.submissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Result of one test. Input/output are only filled for sample tests in RUN mode. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestOutcome(int index, Verdict verdict, long timeMs, long memoryKb,
                          String input, String output, String expected, String stderr) {
}
