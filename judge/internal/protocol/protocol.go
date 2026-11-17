// Package protocol holds the messages exchanged between the core, the judge
// worker and the in-sandbox runner (boxrun), plus the supported languages.
package protocol

// Verdict of a single test or of a whole submission.
type Verdict string

const (
	Accepted            Verdict = "ACCEPTED"
	WrongAnswer         Verdict = "WRONG_ANSWER"
	TimeLimitExceeded   Verdict = "TIME_LIMIT_EXCEEDED"
	MemoryLimitExceeded Verdict = "MEMORY_LIMIT_EXCEEDED"
	OutputLimitExceeded Verdict = "OUTPUT_LIMIT_EXCEEDED"
	RuntimeError        Verdict = "RUNTIME_ERROR"
	CompilationError    Verdict = "COMPILATION_ERROR"
	SystemError         Verdict = "SYSTEM_ERROR"
)

// Mode of a submission: RUN only executes the sample tests and shows their output,
// SUBMIT runs every test and stops at the first failure.
type Mode string

const (
	ModeRun    Mode = "RUN"
	ModeSubmit Mode = "SUBMIT"
)

// TestCase sent by the core. Expected outputs of hidden tests never leave the judge.
type TestCase struct {
	Index    int    `json:"index"`
	Input    string `json:"input"`
	Expected string `json:"expected"`
	Sample   bool   `json:"sample"`
}

// Job is read from the jobs stream.
type Job struct {
	SubmissionID  string     `json:"submissionId"`
	Language      string     `json:"language"`
	Mode          Mode       `json:"mode"`
	Source        string     `json:"source"`
	TimeLimitMs   int        `json:"timeLimitMs"`
	MemoryLimitMb int        `json:"memoryLimitMb"`
	Tests         []TestCase `json:"tests"`
}

// Plan is what the worker writes to boxrun's standard input.
type Plan struct {
	Language      string     `json:"language"`
	Source        string     `json:"source"`
	TimeLimitMs   int        `json:"timeLimitMs"`
	MemoryLimitMb int        `json:"memoryLimitMb"`
	StopOnFailure bool       `json:"stopOnFailure"`
	RevealOutput  bool       `json:"revealOutput"`
	Tests         []TestCase `json:"tests"`
}

// Event is one JSON line printed by boxrun.
type Event struct {
	Type          string      `json:"type"` // "compiled", "test" or "done"
	OK            bool        `json:"ok,omitempty"`
	CompileOutput string      `json:"compileOutput,omitempty"`
	Test          *TestResult `json:"test,omitempty"`
}

// TestResult of one test case.
type TestResult struct {
	Index    int     `json:"index"`
	Verdict  Verdict `json:"verdict"`
	TimeMs   int64   `json:"timeMs"`
	MemoryKb int64   `json:"memoryKb"`
	// Only filled for sample tests in RUN mode.
	Input    string `json:"input,omitempty"`
	Output   string `json:"output,omitempty"`
	Expected string `json:"expected,omitempty"`
	Stderr   string `json:"stderr,omitempty"`
}

// Result types written to the results stream.
const (
	ResultStarted  = "STARTED"
	ResultProgress = "PROGRESS"
	ResultFinal    = "RESULT"
)

// Result is written to the results stream for the core.
type Result struct {
	SubmissionID  string       `json:"submissionId"`
	Type          string       `json:"type"`
	Total         int          `json:"total"`
	Test          *TestResult  `json:"test,omitempty"`
	Verdict       Verdict      `json:"verdict,omitempty"`
	Passed        int          `json:"passed"`
	TimeMs        int64        `json:"timeMs"`
	MemoryKb      int64        `json:"memoryKb"`
	CompileOutput string       `json:"compileOutput,omitempty"`
	Tests         []TestResult `json:"tests,omitempty"`
	Message       string       `json:"message,omitempty"`
}
