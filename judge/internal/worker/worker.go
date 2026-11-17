// Package worker turns a judge job into a sandbox run and reports the results.
package worker

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

const (
	MaxSourceBytes = 64 << 10
	MaxTests       = 100
)

// Sandbox runs a plan and streams boxrun events.
type Sandbox interface {
	Run(ctx context.Context, plan protocol.Plan, onEvent func(protocol.Event)) error
}

// Publisher sends results back to the core.
type Publisher interface {
	Publish(ctx context.Context, result protocol.Result) error
}

type Worker struct {
	Sandbox   Sandbox
	Publisher Publisher
	Log       *slog.Logger
}

// PlanFor selects the tests and options for a job. RUN only uses the sample
// tests and shows their output; SUBMIT runs everything and stops at the first failure.
func PlanFor(job protocol.Job) protocol.Plan {
	plan := protocol.Plan{
		Language:      job.Language,
		Source:        job.Source,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
	}
	if job.Mode == protocol.ModeRun {
		for _, t := range job.Tests {
			if t.Sample {
				plan.Tests = append(plan.Tests, t)
			}
		}
		plan.RevealOutput = true
	} else {
		plan.Tests = job.Tests
		plan.StopOnFailure = true
	}
	return plan
}

// Validate rejects jobs the sandbox must never see.
func Validate(job protocol.Job) error {
	if _, err := protocol.LanguageByID(job.Language); err != nil {
		return err
	}
	switch {
	case job.SubmissionID == "":
		return fmt.Errorf("missing submission id")
	case len(job.Source) == 0:
		return fmt.Errorf("empty source code")
	case len(job.Source) > MaxSourceBytes:
		return fmt.Errorf("source code is larger than %d KB", MaxSourceBytes>>10)
	case len(job.Tests) == 0 || len(job.Tests) > MaxTests:
		return fmt.Errorf("a problem needs between 1 and %d tests", MaxTests)
	case job.TimeLimitMs < 100 || job.TimeLimitMs > 15000:
		return fmt.Errorf("time limit must be between 100 ms and 15 s")
	case job.MemoryLimitMb < 16 || job.MemoryLimitMb > 1024:
		return fmt.Errorf("memory limit must be between 16 and 1024 MB")
	}
	return nil
}

// Process judges one job. Errors are only returned when publishing failed,
// so the job can be retried; judging problems become a SYSTEM_ERROR result.
func (w *Worker) Process(ctx context.Context, job protocol.Job) error {
	log := w.Log.With("submission", job.SubmissionID, "language", job.Language, "mode", job.Mode)
	if err := Validate(job); err != nil {
		log.Warn("rejected job", "error", err)
		return w.Publisher.Publish(ctx, protocol.Result{
			SubmissionID: job.SubmissionID, Type: protocol.ResultFinal,
			Verdict: protocol.SystemError, Message: err.Error(),
		})
	}

	plan := PlanFor(job)
	total := len(plan.Tests)
	if err := w.Publisher.Publish(ctx, protocol.Result{SubmissionID: job.SubmissionID, Type: protocol.ResultStarted, Total: total}); err != nil {
		return err
	}

	final := protocol.Result{SubmissionID: job.SubmissionID, Type: protocol.ResultFinal, Total: total}
	compiled := false
	var publishErr error
	runErr := w.Sandbox.Run(ctx, plan, func(e protocol.Event) {
		switch e.Type {
		case "compiled":
			compiled = e.OK
			final.CompileOutput = e.CompileOutput
		case "test":
			if e.Test == nil {
				return
			}
			final.Tests = append(final.Tests, *e.Test)
			progress := protocol.Result{SubmissionID: job.SubmissionID, Type: protocol.ResultProgress, Total: total, Test: e.Test}
			if err := w.Publisher.Publish(ctx, progress); err != nil && publishErr == nil {
				publishErr = err
			}
		}
	})
	if publishErr != nil {
		return publishErr
	}

	switch {
	case runErr != nil:
		log.Error("sandbox error", "error", runErr)
		final.Verdict = protocol.SystemError
		final.Message = "The judge could not run this submission. Please try again."
	case !compiled:
		final.Verdict = protocol.CompilationError
	default:
		final.Verdict = Summarise(&final, total)
	}
	log.Info("judged", "verdict", final.Verdict, "passed", final.Passed, "total", total, "timeMs", final.TimeMs)
	return w.Publisher.Publish(ctx, final)
}

// Summarise fills passed/time/memory and returns the overall verdict:
// the first failing test decides, otherwise ACCEPTED.
func Summarise(final *protocol.Result, total int) protocol.Verdict {
	verdict := protocol.Accepted
	for _, t := range final.Tests {
		final.TimeMs = max(final.TimeMs, t.TimeMs)
		final.MemoryKb = max(final.MemoryKb, t.MemoryKb)
		if t.Verdict == protocol.Accepted {
			final.Passed++
		} else if verdict == protocol.Accepted {
			verdict = t.Verdict
		}
	}
	if verdict == protocol.Accepted && final.Passed < total {
		// The sandbox stopped early without a failing test: never claim success.
		return protocol.SystemError
	}
	return verdict
}
