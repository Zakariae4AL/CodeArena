package boxrun

import (
	"os"
	"syscall"
	"time"
)

// killAll removes every process the program started.
//
// Inside the sandbox boxrun is PID 1, so kill(-1) reaches every other process in
// the container, even ones that left the process group with setsid(). It is
// repeated until nothing is left, because a fork bomb keeps creating processes
// while we kill them, and orphans are reaped so they do not use up the pids limit.
//
// Outside a container (tests) only the program's process group is killed.
func killAll(pgid int) {
	for range 100 {
		var err error
		if os.Getpid() == 1 {
			err = syscall.Kill(-1, syscall.SIGKILL)
			reapZombies()
		} else {
			err = syscall.Kill(-pgid, syscall.SIGKILL)
		}
		if err == syscall.ESRCH {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func reapZombies() {
	for {
		var status syscall.WaitStatus
		pid, err := syscall.Wait4(-1, &status, syscall.WNOHANG, nil)
		if pid <= 0 || err != nil {
			return
		}
	}
}
