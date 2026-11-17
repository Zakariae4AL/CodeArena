package boxrun

import "sync"

// limitedBuffer keeps at most max bytes and remembers that more was written.
// When the limit is crossed it calls onOverflow once (used to kill the program).
type limitedBuffer struct {
	mu         sync.Mutex
	buf        []byte
	max        int
	overflow   bool
	onOverflow func()
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	room := b.max - len(b.buf)
	if len(p) > room {
		if room > 0 {
			b.buf = append(b.buf, p[:room]...)
		}
		if !b.overflow {
			b.overflow = true
			if b.onOverflow != nil {
				go b.onOverflow()
			}
		}
		// Report success so the writer keeps going until it is killed.
		return len(p), nil
	}
	b.buf = append(b.buf, p...)
	return len(p), nil
}

func (b *limitedBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return string(b.buf)
}

func (b *limitedBuffer) Overflowed() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.overflow
}

func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max] + "\n…(truncated)"
}
