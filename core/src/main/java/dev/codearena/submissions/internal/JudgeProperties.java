package dev.codearena.submissions.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param queue          "redis" in normal use, "memory" to keep jobs in memory (tests, UI work without a judge)
 * @param jobsStream     Redis stream the judges read jobs from
 * @param resultsStream  Redis stream the judges write results to
 * @param group          consumer group of the core instances on the results stream
 * @param consumerName   unique name of this core instance in the group
 * @param submissionsPerMinute rate limit per user
 */
@ConfigurationProperties("codearena.judge")
public record JudgeProperties(String queue, String jobsStream, String resultsStream, String group, String consumerName,
                       int submissionsPerMinute) {
}
