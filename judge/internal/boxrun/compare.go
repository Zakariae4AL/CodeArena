package boxrun

import "strings"

// SameOutput compares program output with the expected answer the way most
// judges do: line endings are normalised, trailing spaces on each line and
// trailing blank lines are ignored. Everything else must match exactly.
func SameOutput(got, want string) bool {
	return normalise(got) == normalise(want)
}

func normalise(s string) string {
	s = strings.ReplaceAll(s, "\r\n", "\n")
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		lines[i] = strings.TrimRight(line, " \t\r")
	}
	for len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	return strings.Join(lines, "\n")
}
