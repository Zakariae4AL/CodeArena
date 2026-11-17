package boxrun

import (
	"context"
	"os"
	"os/exec"
	"strings"
	"testing"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

// These tests run real compilers and interpreters on the machine running the
// tests (no Docker needed). A language whose tools are missing is skipped.

var tools = map[string]string{"cpp": "g++", "java": "javac", "python": "python3", "javascript": "node"}

func needs(t *testing.T, lang string) {
	t.Helper()
	if _, err := exec.LookPath(tools[lang]); err != nil {
		t.Skipf("%s not installed", tools[lang])
	}
}

func workDir(t *testing.T) string {
	// When the tests run as root the program runs as "nobody": give it a
	// world-writable directory, like the /box tmpfs in the real sandbox.
	dir, err := os.MkdirTemp("", "boxrun-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(dir) })
	if err := os.Chmod(dir, 0o1777); err != nil {
		t.Fatal(err)
	}
	return dir
}

func run(t *testing.T, plan protocol.Plan) (compiled protocol.Event, results []protocol.TestResult) {
	t.Helper()
	if plan.TimeLimitMs == 0 {
		plan.TimeLimitMs = 1000
	}
	if plan.MemoryLimitMb == 0 {
		plan.MemoryLimitMb = 256
	}
	var done bool
	err := Run(context.Background(), plan, Options{Dir: workDir(t), UID: 65534, GID: 65534}, func(e protocol.Event) {
		switch e.Type {
		case "compiled":
			compiled = e
		case "test":
			results = append(results, *e.Test)
		case "done":
			done = true
		}
	})
	if err != nil {
		t.Fatal(err)
	}
	if !done {
		t.Fatal("no done event")
	}
	return compiled, results
}

func sumTests() []protocol.TestCase {
	return []protocol.TestCase{
		{Index: 1, Input: "1 2\n", Expected: "3\n", Sample: true},
		{Index: 2, Input: "40 2\n", Expected: "42"},
		{Index: 3, Input: "-5 5\n", Expected: "0\n"},
	}
}

var sumSolutions = map[string]string{
	"cpp": `#include <iostream>
int main() { long long a, b; std::cin >> a >> b; std::cout << a + b << "\n"; }`,
	"java": `import java.util.Scanner;
public class Main { public static void main(String[] args) {
  Scanner in = new Scanner(System.in); long a = in.nextLong(), b = in.nextLong(); System.out.println(a + b); } }`,
	"python": "a, b = map(int, input().split())\nprint(a + b)\n",
	"javascript": `const [a, b] = require('fs').readFileSync(0, 'utf8').trim().split(/\s+/).map(Number);
console.log(a + b);`,
}

func TestAcceptedInEveryLanguage(t *testing.T) {
	for lang, source := range sumSolutions {
		t.Run(lang, func(t *testing.T) {
			needs(t, lang)
			compiled, results := run(t, protocol.Plan{Language: lang, Source: source, Tests: sumTests(), MemoryLimitMb: 256})
			if !compiled.OK {
				t.Fatalf("compile failed: %s", compiled.CompileOutput)
			}
			if len(results) != 3 {
				t.Fatalf("got %d results", len(results))
			}
			for _, r := range results {
				if r.Verdict != protocol.Accepted {
					t.Errorf("test %d: %s", r.Index, r.Verdict)
				}
			}
		})
	}
}

func TestVerdicts(t *testing.T) {
	cases := []struct {
		name, lang, source string
		memoryMb           int
		want               protocol.Verdict
	}{
		{"wrong answer", "python", "print(5)", 0, protocol.WrongAnswer},
		{"runtime error", "python", "raise SystemExit(3)", 0, protocol.RuntimeError},
		{"exception", "javascript", "throw new Error('boom')", 0, protocol.RuntimeError},
		{"infinite loop", "cpp", "int main(){ volatile long x=0; for(;;) x++; }", 0, protocol.TimeLimitExceeded},
		{"sleeping forever", "python", "import time\ntime.sleep(60)", 0, protocol.TimeLimitExceeded},
		{"too much memory", "cpp", `#include <vector>
#include <cstdio>
int main(){ std::vector<char> v(300u<<20, 1); long s=0; for (char c : v) s += c; printf("%ld\n", s); }`, 64, protocol.MemoryLimitExceeded},
		{"python memory", "python", "x = bytearray(300 * 1024 * 1024)\nprint(len(x))", 64, protocol.MemoryLimitExceeded},
		{"output flood", "cpp", `#include <cstdio>
int main(){ for(;;) fputs("spam spam spam spam spam\n", stdout); }`, 0, protocol.OutputLimitExceeded},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			needs(t, tc.lang)
			_, results := run(t, protocol.Plan{
				Language: tc.lang, Source: tc.source, MemoryLimitMb: tc.memoryMb,
				Tests: []protocol.TestCase{{Index: 1, Input: "1 2\n", Expected: "3\n"}},
			})
			if len(results) != 1 || results[0].Verdict != tc.want {
				t.Fatalf("want %s, got %+v", tc.want, results)
			}
		})
	}
}

func TestCompilationErrorStopsEverything(t *testing.T) {
	needs(t, "cpp")
	compiled, results := run(t, protocol.Plan{Language: "cpp", Source: "int main( { return 0 }", Tests: sumTests()})
	if compiled.OK || !strings.Contains(compiled.CompileOutput, "error") {
		t.Fatalf("expected a compile error, got %+v", compiled)
	}
	if len(results) != 0 {
		t.Fatalf("no test should run, got %d", len(results))
	}
}

func TestPythonSyntaxErrorIsACompilationError(t *testing.T) {
	needs(t, "python")
	compiled, _ := run(t, protocol.Plan{Language: "python", Source: "print(", Tests: sumTests()})
	if compiled.OK {
		t.Fatal("expected a compilation error")
	}
}

func TestStopOnFirstFailure(t *testing.T) {
	needs(t, "python")
	source := "a, b = map(int, input().split())\nprint(a + b if a != 40 else 0)\n"
	_, results := run(t, protocol.Plan{Language: "python", Source: source, Tests: sumTests(), StopOnFailure: true})
	if len(results) != 2 || results[1].Verdict != protocol.WrongAnswer {
		t.Fatalf("expected to stop after test 2, got %+v", results)
	}
}

func TestRevealsSampleOutputOnly(t *testing.T) {
	needs(t, "python")
	_, results := run(t, protocol.Plan{Language: "python", Source: "print('hello')\nimport sys\nprint('debug', file=sys.stderr)", Tests: sumTests(), RevealOutput: true})
	if results[0].Output != "hello\n" || results[0].Expected != "3\n" || !strings.Contains(results[0].Stderr, "debug") {
		t.Fatalf("sample output not revealed: %+v", results[0])
	}
	if results[1].Output != "" || results[1].Expected != "" {
		t.Fatalf("hidden test leaked: %+v", results[1])
	}
}

func TestProgramCannotReadOtherUsersFiles(t *testing.T) {
	if os.Getuid() != 0 {
		t.Skip("needs root to drop privileges")
	}
	needs(t, "python")
	secret := t.TempDir() // 0700, owned by root
	if err := os.WriteFile(secret+"/answers.txt", []byte("42"), 0o600); err != nil {
		t.Fatal(err)
	}
	source := "import os\ntry:\n    print(open('" + secret + "/answers.txt').read())\nexcept PermissionError:\n    print('denied')\nprint(os.getuid())"
	_, results := run(t, protocol.Plan{Language: "python", Source: source, RevealOutput: true,
		Tests: []protocol.TestCase{{Index: 1, Expected: "denied\n65534\n", Sample: true}}})
	if results[0].Verdict != protocol.Accepted {
		t.Fatalf("program escaped its user: %+v", results[0])
	}
}

func TestSameOutput(t *testing.T) {
	ok := [][2]string{{"3", "3\n"}, {"1 2  \r\n3\n\n", "1 2\n3"}, {"", "\n\n"}}
	bad := [][2]string{{"3", "4"}, {"1  2", "1 2"}, {"a\n\nb", "a\nb"}}
	for _, p := range ok {
		if !SameOutput(p[0], p[1]) {
			t.Errorf("%q should equal %q", p[0], p[1])
		}
	}
	for _, p := range bad {
		if SameOutput(p[0], p[1]) {
			t.Errorf("%q should differ from %q", p[0], p[1])
		}
	}
}
