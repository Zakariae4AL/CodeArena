package dev.codearena.submissions.internal;

/** Sends jobs to the judge workers. */
interface JudgeQueue {

    void send(JudgeProtocol.Job job);
}
