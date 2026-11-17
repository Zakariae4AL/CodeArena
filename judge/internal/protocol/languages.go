package protocol

import (
	"fmt"
	"strconv"
)

// Language describes how to compile and run one language inside the sandbox.
type Language struct {
	ID         string
	Name       string
	SourceFile string
	// Compile is optional; for interpreted languages it is a syntax check.
	Compile []string
	// Run builds the run command for a memory limit in MB.
	Run func(memoryMb int) []string
	// TimeFactor gives slower runtimes a fair time limit (C++ = 1).
	TimeFactor float64
	// MeasureRSS is false for runtimes whose baseline memory (JVM, V8) would
	// distort the result; they enforce the limit through their own heap flag.
	MeasureRSS bool
	// OOMMarker is printed on stderr when the runtime's own heap limit is hit.
	OOMMarker string
	// Env added to every command.
	Env []string
}

var languages = map[string]Language{
	"cpp": {
		ID: "cpp", Name: "C++20", SourceFile: "main.cpp",
		Compile:    []string{"g++", "-O2", "-std=c++20", "-pipe", "-o", "main", "main.cpp"},
		Run:        func(int) []string { return []string{"./main"} },
		TimeFactor: 1, MeasureRSS: true,
	},
	"java": {
		ID: "java", Name: "Java 25", SourceFile: "Main.java",
		Compile: []string{"javac", "-J-Xmx384m", "-J-XX:+UseSerialGC", "-J-XX:TieredStopAtLevel=1", "-encoding", "UTF-8", "Main.java"},
		Run: func(mb int) []string {
			return []string{"java", "-Xmx" + strconv.Itoa(mb) + "m", "-Xss64m", "-XX:+UseSerialGC",
				"-XX:TieredStopAtLevel=1", "-XX:-UsePerfData", "-Dfile.encoding=UTF-8", "Main"}
		},
		TimeFactor: 2, MeasureRSS: false, OOMMarker: "java.lang.OutOfMemoryError",
	},
	"python": {
		ID: "python", Name: "Python 3", SourceFile: "main.py",
		Compile:    []string{"python3", "-m", "py_compile", "main.py"},
		Run:        func(int) []string { return []string{"python3", "-S", "main.py"} },
		TimeFactor: 3, MeasureRSS: true, OOMMarker: "MemoryError",
		Env: []string{"PYTHONDONTWRITEBYTECODE=1", "PYTHONIOENCODING=utf-8"},
	},
	"javascript": {
		ID: "javascript", Name: "JavaScript (Node.js)", SourceFile: "main.js",
		Compile: []string{"node", "--check", "main.js"},
		Run: func(mb int) []string {
			return []string{"node", "--max-old-space-size=" + strconv.Itoa(mb), "--stack-size=65500", "main.js"}
		},
		TimeFactor: 1.5, MeasureRSS: false, OOMMarker: "heap out of memory",
	},
}

// LanguageByID returns a supported language.
func LanguageByID(id string) (Language, error) {
	lang, ok := languages[id]
	if !ok {
		return Language{}, fmt.Errorf("unsupported language %q", id)
	}
	return lang, nil
}

// LanguageIDs lists the supported languages.
func LanguageIDs() []string {
	return []string{"cpp", "java", "python", "javascript"}
}

// EffectiveTimeLimitMs applies the language time factor.
func (l Language) EffectiveTimeLimitMs(limitMs int) int64 {
	return int64(float64(limitMs) * l.TimeFactor)
}
