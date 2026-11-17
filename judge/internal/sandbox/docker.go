// Package sandbox runs boxrun inside a locked-down, throwaway Docker container.
package sandbox

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

// Config of the Docker sandbox.
type Config struct {
	// Docker CLI binary.
	Binary string
	// ImagePrefix + language id = runner image, e.g. "codearena/runner-" + "cpp".
	ImagePrefix string
	// Optional OCI runtime for stronger isolation, e.g. "runsc" (gVisor).
	Runtime   string
	CPUs      string
	PidsLimit int
}

// Docker runs plans in containers with no network, a read-only root file
// system, dropped capabilities and hard limits on memory, CPU and processes.
type Docker struct {
	cfg Config
}

func NewDocker(cfg Config) *Docker {
	if cfg.Binary == "" {
		cfg.Binary = "docker"
	}
	if cfg.ImagePrefix == "" {
		cfg.ImagePrefix = "codearena/runner-"
	}
	if cfg.CPUs == "" {
		cfg.CPUs = "1"
	}
	if cfg.PidsLimit == 0 {
		cfg.PidsLimit = 128
	}
	return &Docker{cfg: cfg}
}

// containerMemoryMb leaves room for the compiler and the runner itself; the
// program's own limit is enforced by boxrun (RSS) or the runtime's heap flag.
func containerMemoryMb(lang string, programMb int) int {
	floor := 512
	if lang == "java" {
		floor = 768
	}
	return max(programMb+128, floor)
}

// Args builds the `docker run` arguments. Exported for tests and for reading.
func (d *Docker) Args(name string, plan protocol.Plan) []string {
	mem := strconv.Itoa(containerMemoryMb(plan.Language, plan.MemoryLimitMb)) + "m"
	args := []string{
		"run", "--rm", "-i",
		"--name", name,
		"--label", "codearena.sandbox=true",
		"--network", "none",
		"--read-only",
		"--tmpfs", "/box:rw,exec,nosuid,nodev,size=64m,mode=1777",
		"--tmpfs", "/tmp:rw,nosuid,nodev,size=64m,mode=1777",
		"--memory", mem,
		"--memory-swap", mem, // no swap
		"--cpus", d.cfg.CPUs,
		"--pids-limit", strconv.Itoa(d.cfg.PidsLimit),
		"--ulimit", "nofile=256:256",
		"--ulimit", "fsize=33554432:33554432", // 32 MB per file
		"--ulimit", "core=0:0",
		// boxrun starts as root only to drop to "nobody" before running code.
		// It keeps three capabilities: SETUID/SETGID to drop privileges and KILL
		// to clean up processes owned by "nobody" (fork bombs, background jobs).
		"--user", "0:0",
		"--cap-drop", "ALL",
		"--cap-add", "SETUID",
		"--cap-add", "SETGID",
		"--cap-add", "KILL",
		"--security-opt", "no-new-privileges",
		"--stop-timeout", "1",
	}
	if d.cfg.Runtime != "" {
		args = append(args, "--runtime", d.cfg.Runtime)
	}
	return append(args, d.cfg.ImagePrefix+plan.Language)
}

// Deadline is the longest a plan may take before the container is killed.
func Deadline(plan protocol.Plan) time.Duration {
	lang, err := protocol.LanguageByID(plan.Language)
	if err != nil {
		return time.Minute
	}
	perTest := time.Duration(lang.EffectiveTimeLimitMs(plan.TimeLimitMs))*time.Millisecond + 2*time.Second
	return 45*time.Second + time.Duration(len(plan.Tests))*perTest
}

// Run starts the container, sends the plan on stdin and calls onEvent for each line.
func (d *Docker) Run(ctx context.Context, plan protocol.Plan, onEvent func(protocol.Event)) error {
	ctx, cancel := context.WithTimeout(ctx, Deadline(plan))
	defer cancel()

	name := "codearena-" + randomID()
	input, err := json.Marshal(plan)
	if err != nil {
		return err
	}
	cmd := exec.CommandContext(ctx, d.cfg.Binary, d.Args(name, plan)...)
	cmd.Stdin = bytes.NewReader(input)
	// On timeout, killing the CLI is not enough: the container must go too.
	cmd.Cancel = func() error {
		_ = exec.Command(d.cfg.Binary, "rm", "-f", name).Run()
		return cmd.Process.Kill()
	}
	cmd.WaitDelay = 5 * time.Second
	var stderr bytes.Buffer
	cmd.Stderr = &limitWriter{w: &stderr, n: 8 << 10}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("start sandbox: %w", err)
	}

	sawDone := false
	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 64<<10), 4<<20)
	for scanner.Scan() {
		var event protocol.Event
		if err := json.Unmarshal(scanner.Bytes(), &event); err != nil {
			continue
		}
		if event.Type == "done" {
			sawDone = true
		}
		onEvent(event)
	}
	waitErr := cmd.Wait()
	switch {
	case errors.Is(ctx.Err(), context.DeadlineExceeded):
		return fmt.Errorf("sandbox timed out after %s", Deadline(plan))
	case !sawDone && waitErr != nil:
		return fmt.Errorf("sandbox failed: %w: %s", waitErr, strings.TrimSpace(stderr.String()))
	case !sawDone:
		return errors.New("sandbox stopped before finishing")
	}
	return nil
}

func randomID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

type limitWriter struct {
	w interface{ Write([]byte) (int, error) }
	n int
}

func (l *limitWriter) Write(p []byte) (int, error) {
	if l.n > 0 {
		chunk := p
		if len(chunk) > l.n {
			chunk = chunk[:l.n]
		}
		l.n -= len(chunk)
		_, _ = l.w.Write(chunk)
	}
	return len(p), nil
}
