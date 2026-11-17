// Package boxrun compiles a submission and runs it against the test cases.
// It runs inside the sandbox container as root, keeps the expected outputs in
// memory only, and starts the user's program as an unprivileged user.
package boxrun

import (
	"context"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

const (
	compileTimeout   = 20 * time.Second
	compileOutputMax = 16 << 10
	stderrMax        = 64 << 10
	minOutputMax     = 1 << 20
	maxOutputMax     = 16 << 20
	revealMax        = 4 << 10
	killGrace        = time.Second
)

// Options control how the runner executes programs.
type Options struct {
	// Dir is the working directory where the source is written.
	Dir string
	// UID/GID the program runs as; ignored when the runner is not root.
	UID, GID int
}

// Run executes the plan and reports each step through emit.
func Run(ctx context.Context, plan protocol.Plan, opts Options, emit func(protocol.Event)) error {
	lang, err := protocol.LanguageByID(plan.Language)
	if err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(opts.Dir, lang.SourceFile), []byte(plan.Source), 0o644); err != nil {
		return err
	}

	if len(lang.Compile) > 0 {
		res := execute(ctx, opts, lang, lang.Compile, "", compileTimeout, compileOutputMax)
		ok := res.exitCode == 0 && !res.timedOut && !res.killed
		output := strings.TrimSpace(res.stderr + "\n" + res.stdout)
		if res.timedOut {
			output = "Compilation took too long.\n" + output
		}
		emit(protocol.Event{Type: "compiled", OK: ok, CompileOutput: truncate(output, compileOutputMax)})
		if !ok {
			emit(protocol.Event{Type: "done"})
			return nil
		}
	} else {
		emit(protocol.Event{Type: "compiled", OK: true})
	}

	limitMs := lang.EffectiveTimeLimitMs(plan.TimeLimitMs)
	wall := time.Duration(limitMs)*time.Millisecond + killGrace
	for _, test := range plan.Tests {
		outputMax := min(max(len(test.Expected)*2, minOutputMax), maxOutputMax)
		res := execute(ctx, opts, lang, lang.Run(plan.MemoryLimitMb), test.Input, wall, outputMax)
		result := judge(res, test, lang, limitMs, plan.MemoryLimitMb)
		if plan.RevealOutput && test.Sample {
			result.Input = truncate(test.Input, revealMax)
			result.Output = truncate(res.stdout, revealMax)
			result.Expected = truncate(test.Expected, revealMax)
			result.Stderr = truncate(res.stderr, revealMax)
		}
		emit(protocol.Event{Type: "test", Test: &result})
		if plan.StopOnFailure && result.Verdict != protocol.Accepted {
			break
		}
	}
	emit(protocol.Event{Type: "done"})
	return nil
}

// judge turns a raw execution into a verdict.
func judge(res execution, test protocol.TestCase, lang protocol.Language, limitMs int64, memoryMb int) protocol.TestResult {
	out := protocol.TestResult{Index: test.Index, TimeMs: res.cpuMs, MemoryKb: res.maxRSSKb}
	memoryExceeded := (lang.MeasureRSS && res.maxRSSKb > int64(memoryMb)*1024) ||
		(lang.OOMMarker != "" && strings.Contains(res.stderr, lang.OOMMarker))
	switch {
	case res.timedOut || res.cpuMs > limitMs:
		out.Verdict = protocol.TimeLimitExceeded
	case res.outputOverflow:
		out.Verdict = protocol.OutputLimitExceeded
	case memoryExceeded:
		out.Verdict = protocol.MemoryLimitExceeded
	case res.killed:
		// Killed by someone else than us: the container's memory cgroup (OOM killer).
		out.Verdict = protocol.MemoryLimitExceeded
	case res.exitCode != 0:
		out.Verdict = protocol.RuntimeError
	case !SameOutput(res.stdout, test.Expected):
		out.Verdict = protocol.WrongAnswer
	default:
		out.Verdict = protocol.Accepted
	}
	return out
}

type execution struct {
	stdout, stderr string
	exitCode       int
	killed         bool // SIGKILL that we did not send
	timedOut       bool
	outputOverflow bool
	cpuMs          int64
	maxRSSKb       int64
}

func execute(ctx context.Context, opts Options, lang protocol.Language, argv []string, stdin string,
	wall time.Duration, outputMax int) execution {
	cmd := exec.Command(argv[0], argv[1:]...)
	cmd.Dir = opts.Dir
	cmd.Env = append([]string{"PATH=" + os.Getenv("PATH"), "HOME=/tmp", "LANG=C.UTF-8"}, lang.Env...)
	cmd.Stdin = strings.NewReader(stdin)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if os.Getuid() == 0 {
		cmd.SysProcAttr.Credential = &syscall.Credential{Uid: uint32(opts.UID), Gid: uint32(opts.GID), NoSetGroups: true}
	}

	// Do not wait forever for pipes kept open by processes that escaped the kill.
	cmd.WaitDelay = 500 * time.Millisecond

	var res execution
	killGroup := func() {
		if cmd.Process != nil {
			_ = syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
		}
	}
	stdout := &limitedBuffer{max: outputMax, onOverflow: killGroup}
	stderr := &limitedBuffer{max: stderrMax}
	cmd.Stdout = stdout
	cmd.Stderr = stderr

	if err := cmd.Start(); err != nil {
		res.exitCode = 127
		res.stderr = err.Error()
		return res
	}

	// The timer runs on another goroutine: it reports through an atomic flag, and we
	// wait for it to finish before reading the result.
	var timedOut atomic.Bool
	fired := make(chan struct{})
	timer := time.AfterFunc(wall, func() {
		defer close(fired)
		timedOut.Store(true)
		killGroup()
	})
	stop := context.AfterFunc(ctx, killGroup)
	err := cmd.Wait()
	if !timer.Stop() {
		<-fired
	}
	stop()
	res.timedOut = timedOut.Load()
	// Kill anything the program left behind (forks, daemons, fork bombs).
	if cmd.Process != nil {
		killAll(cmd.Process.Pid)
	}

	res.stdout = stdout.String()
	res.stderr = stderr.String()
	res.outputOverflow = stdout.Overflowed()
	if state := cmd.ProcessState; state != nil {
		if usage, ok := state.SysUsage().(*syscall.Rusage); ok {
			res.cpuMs = (usage.Utime.Nano() + usage.Stime.Nano()) / int64(time.Millisecond)
			res.maxRSSKb = usage.Maxrss
		}
		if status, ok := state.Sys().(syscall.WaitStatus); ok && status.Signaled() {
			res.exitCode = 128 + int(status.Signal())
			res.killed = status.Signal() == syscall.SIGKILL && !res.timedOut && !res.outputOverflow
		} else {
			res.exitCode = state.ExitCode()
		}
	}
	var exitErr *exec.ExitError
	if err != nil && !errors.As(err, &exitErr) && !errors.Is(err, exec.ErrWaitDelay) {
		res.exitCode = 127
		res.stderr += err.Error()
	}
	return res
}
