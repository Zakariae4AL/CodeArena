package sandbox

import (
	"context"
	"os"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

func TestContainerIsLockedDown(t *testing.T) {
	d := NewDocker(Config{Runtime: "runsc"})
	args := strings.Join(d.Args("box1", protocol.Plan{Language: "cpp", MemoryLimitMb: 256}), " ")
	for _, flag := range []string{
		"--network none", "--read-only", "--cap-drop ALL", "--security-opt no-new-privileges",
		"--pids-limit 128", "--memory 512m", "--memory-swap 512m", "--cpus 1", "--rm",
		"--runtime runsc", "--ulimit core=0:0", "--cap-add KILL",
	} {
		if !strings.Contains(args, flag) {
			t.Errorf("missing %q in %s", flag, args)
		}
	}
	if !strings.HasSuffix(args, "codearena/runner-cpp") {
		t.Errorf("wrong image: %s", args)
	}
	if slices.Contains(strings.Fields(args), "--privileged") {
		t.Error("never privileged")
	}
}

func TestMemoryLeavesRoomForCompilers(t *testing.T) {
	if got := containerMemoryMb("java", 256); got != 768 {
		t.Errorf("java: %d", got)
	}
	if got := containerMemoryMb("cpp", 1024); got != 1152 {
		t.Errorf("cpp: %d", got)
	}
}

func TestDeadlineGrowsWithTests(t *testing.T) {
	small := Deadline(protocol.Plan{Language: "python", TimeLimitMs: 1000, Tests: make([]protocol.TestCase, 1)})
	big := Deadline(protocol.Plan{Language: "python", TimeLimitMs: 1000, Tests: make([]protocol.TestCase, 10)})
	if big-small != 9*5*time.Second {
		t.Fatalf("small=%s big=%s", small, big)
	}
}

// Runs real containers: JUDGE_DOCKER_IT=1 go test ./internal/sandbox/
// The runner images must be built first (see judge/README or docker compose).
func TestRealSandbox(t *testing.T) {
	if os.Getenv("JUDGE_DOCKER_IT") != "1" {
		t.Skip("set JUDGE_DOCKER_IT=1 to run Docker sandbox tests")
	}
	d := NewDocker(Config{ImagePrefix: os.Getenv("JUDGE_IMAGE_PREFIX")})
	run := func(lang, source string) []protocol.Event {
		var events []protocol.Event
		plan := protocol.Plan{Language: lang, Source: source, TimeLimitMs: 1000, MemoryLimitMb: 128, RevealOutput: true,
			Tests: []protocol.TestCase{{Index: 1, Input: "", Expected: "ok\n", Sample: true}}}
		if err := d.Run(context.Background(), plan, func(e protocol.Event) { events = append(events, e) }); err != nil {
			t.Fatal(err)
		}
		return events
	}
	verdict := func(events []protocol.Event) protocol.TestResult {
		for _, e := range events {
			if e.Type == "test" {
				return *e.Test
			}
		}
		t.Fatalf("no test event in %+v", events)
		return protocol.TestResult{}
	}

	t.Run("no network", func(t *testing.T) {
		r := verdict(run("python", `import socket
try:
    socket.create_connection(("1.1.1.1", 53), timeout=2)
    print("connected")
except OSError:
    print("ok")`))
		if r.Verdict != protocol.Accepted {
			t.Fatalf("network reachable from the sandbox: %+v", r)
		}
	})
	t.Run("read-only root and non-root user", func(t *testing.T) {
		r := verdict(run("python", `import os
try:
    open("/etc/hacked", "w")
    print("wrote /etc")
except OSError:
    print("ok" if os.getuid() != 0 else "root!")`))
		if r.Verdict != protocol.Accepted {
			t.Fatalf("%+v", r)
		}
	})
	t.Run("fork bomb is contained", func(t *testing.T) {
		start := time.Now()
		r := verdict(run("cpp", `#include <unistd.h>
int main(){ for(;;) fork(); }`))
		if r.Verdict == protocol.Accepted || time.Since(start) > 30*time.Second {
			t.Fatalf("fork bomb: %+v after %s", r, time.Since(start))
		}
	})
	t.Run("memory cgroup", func(t *testing.T) {
		r := verdict(run("cpp", `#include <cstdlib>
#include <cstring>
int main(){ for(;;){ char* p=(char*)malloc(1<<20); memset(p,1,1<<20);} }`))
		if r.Verdict != protocol.MemoryLimitExceeded {
			t.Fatalf("%+v", r)
		}
	})
}
