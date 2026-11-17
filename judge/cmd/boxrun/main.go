// boxrun is the entrypoint of every sandbox container. It reads a plan as JSON
// on standard input and prints one JSON event per line on standard output.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"

	"github.com/Zakariae4AL/CodeArena/judge/internal/boxrun"
	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

func main() {
	dir := flag.String("dir", "/box", "working directory (a writable tmpfs in the sandbox)")
	uid := flag.Int("uid", 65534, "user id the program runs as")
	gid := flag.Int("gid", 65534, "group id the program runs as")
	flag.Parse()

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer cancel()

	var plan protocol.Plan
	data, err := io.ReadAll(io.LimitReader(os.Stdin, 256<<20))
	if err == nil {
		err = json.Unmarshal(data, &plan)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "boxrun: invalid plan:", err)
		os.Exit(2)
	}

	out := json.NewEncoder(os.Stdout)
	emit := func(e protocol.Event) { _ = out.Encode(e) }
	if err := boxrun.Run(ctx, plan, boxrun.Options{Dir: *dir, UID: *uid, GID: *gid}, emit); err != nil {
		fmt.Fprintln(os.Stderr, "boxrun:", err)
		os.Exit(1)
	}
}
