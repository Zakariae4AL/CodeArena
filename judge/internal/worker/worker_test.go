package worker

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"strings"
	"testing"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

type fakeSandbox struct {
	events []protocol.Event
	err    error
	plan   protocol.Plan
}

func (f *fakeSandbox) Run(_ context.Context, plan protocol.Plan, on func(protocol.Event)) error {
	f.plan = plan
	for _, e := range f.events {
		on(e)
	}
	return f.err
}

type recorder struct{ results []protocol.Result }

func (r *recorder) Publish(_ context.Context, res protocol.Result) error {
	r.results = append(r.results, res)
	return nil
}

func job(mode protocol.Mode) protocol.Job {
	return protocol.Job{
		SubmissionID: "s1", Language: "python", Mode: mode, Source: "print(1)",
		TimeLimitMs: 1000, MemoryLimitMb: 256,
		Tests: []protocol.TestCase{
			{Index: 1, Input: "a", Expected: "1", Sample: true},
			{Index: 2, Input: "b", Expected: "2"},
			{Index: 3, Input: "c", Expected: "3"},
		},
	}
}

func test(i int, v protocol.Verdict, ms int64) protocol.Event {
	return protocol.Event{Type: "test", Test: &protocol.TestResult{Index: i, Verdict: v, TimeMs: ms, MemoryKb: ms * 10}}
}

func process(t *testing.T, sb *fakeSandbox, j protocol.Job) []protocol.Result {
	t.Helper()
	rec := &recorder{}
	w := Worker{Sandbox: sb, Publisher: rec, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	if err := w.Process(context.Background(), j); err != nil {
		t.Fatal(err)
	}
	return rec.results
}

func TestRunModeOnlyUsesSamplesAndRevealsOutput(t *testing.T) {
	plan := PlanFor(job(protocol.ModeRun))
	if len(plan.Tests) != 1 || !plan.RevealOutput || plan.StopOnFailure {
		t.Fatalf("bad RUN plan: %+v", plan)
	}
	plan = PlanFor(job(protocol.ModeSubmit))
	if len(plan.Tests) != 3 || plan.RevealOutput || !plan.StopOnFailure {
		t.Fatalf("bad SUBMIT plan: %+v", plan)
	}
}

func TestAcceptedSubmissionStreamsProgress(t *testing.T) {
	sb := &fakeSandbox{events: []protocol.Event{
		{Type: "compiled", OK: true},
		test(1, protocol.Accepted, 5), test(2, protocol.Accepted, 12), test(3, protocol.Accepted, 7),
		{Type: "done"},
	}}
	results := process(t, sb, job(protocol.ModeSubmit))

	if results[0].Type != protocol.ResultStarted || results[0].Total != 3 {
		t.Fatalf("first result should be STARTED: %+v", results[0])
	}
	progress := 0
	for _, r := range results {
		if r.Type == protocol.ResultProgress {
			progress++
		}
	}
	final := results[len(results)-1]
	if progress != 3 || final.Verdict != protocol.Accepted || final.Passed != 3 || final.TimeMs != 12 || final.MemoryKb != 120 {
		t.Fatalf("progress=%d final=%+v", progress, final)
	}
}

func TestFirstFailingTestDecidesTheVerdict(t *testing.T) {
	sb := &fakeSandbox{events: []protocol.Event{
		{Type: "compiled", OK: true}, test(1, protocol.Accepted, 5), test(2, protocol.TimeLimitExceeded, 1000), {Type: "done"},
	}}
	final := process(t, sb, job(protocol.ModeSubmit))
	last := final[len(final)-1]
	if last.Verdict != protocol.TimeLimitExceeded || last.Passed != 1 {
		t.Fatalf("got %+v", last)
	}
}

func TestCompilationError(t *testing.T) {
	sb := &fakeSandbox{events: []protocol.Event{{Type: "compiled", OK: false, CompileOutput: "main.py: SyntaxError"}, {Type: "done"}}}
	results := process(t, sb, job(protocol.ModeSubmit))
	last := results[len(results)-1]
	if last.Verdict != protocol.CompilationError || !strings.Contains(last.CompileOutput, "SyntaxError") {
		t.Fatalf("got %+v", last)
	}
}

func TestSandboxFailureIsASystemErrorNotAVerdict(t *testing.T) {
	sb := &fakeSandbox{err: errors.New("docker: image not found")}
	results := process(t, sb, job(protocol.ModeSubmit))
	last := results[len(results)-1]
	if last.Verdict != protocol.SystemError || strings.Contains(last.Message, "docker") {
		t.Fatalf("internal errors must not leak to users: %+v", last)
	}
}

func TestMissingTestResultsNeverCountAsAccepted(t *testing.T) {
	sb := &fakeSandbox{events: []protocol.Event{{Type: "compiled", OK: true}, test(1, protocol.Accepted, 5), {Type: "done"}}}
	results := process(t, sb, job(protocol.ModeSubmit))
	if v := results[len(results)-1].Verdict; v != protocol.SystemError {
		t.Fatalf("got %s", v)
	}
}

func TestInvalidJobsAreRejectedBeforeTheSandbox(t *testing.T) {
	for name, mutate := range map[string]func(*protocol.Job){
		"language": func(j *protocol.Job) { j.Language = "brainfuck" },
		"empty":    func(j *protocol.Job) { j.Source = "" },
		"huge":     func(j *protocol.Job) { j.Source = strings.Repeat("x", MaxSourceBytes+1) },
		"no tests": func(j *protocol.Job) { j.Tests = nil },
		"time":     func(j *protocol.Job) { j.TimeLimitMs = 60_000 },
		"memory":   func(j *protocol.Job) { j.MemoryLimitMb = 4096 },
	} {
		t.Run(name, func(t *testing.T) {
			j := job(protocol.ModeSubmit)
			mutate(&j)
			sb := &fakeSandbox{}
			results := process(t, sb, j)
			if sb.plan.Language != "" {
				t.Fatal("sandbox must not run")
			}
			if len(results) != 1 || results[0].Verdict != protocol.SystemError {
				t.Fatalf("got %+v", results)
			}
		})
	}
}
