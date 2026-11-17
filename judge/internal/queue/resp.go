// Package queue implements the Redis Streams job queue with a tiny RESP client,
// so the judge has no third-party dependencies.
package queue

import (
	"bufio"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"time"
)

// RedisError is an error reply from the server (for example "BUSYGROUP ...").
type RedisError string

func (e RedisError) Error() string { return string(e) }

// conn is one connection speaking RESP2. It is not safe for concurrent use.
type conn struct {
	nc net.Conn
	r  *bufio.Reader
	w  *bufio.Writer
}

// Options to reach Redis.
type Options struct {
	Addr     string
	Username string
	Password string
	TLS      bool
}

func dial(ctx context.Context, opts Options) (*conn, error) {
	dialer := &net.Dialer{Timeout: 5 * time.Second, KeepAlive: 30 * time.Second}
	var nc net.Conn
	var err error
	if opts.TLS {
		host, _, _ := net.SplitHostPort(opts.Addr)
		nc, err = (&tls.Dialer{NetDialer: dialer, Config: &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12}}).DialContext(ctx, "tcp", opts.Addr)
	} else {
		nc, err = dialer.DialContext(ctx, "tcp", opts.Addr)
	}
	if err != nil {
		return nil, err
	}
	c := &conn{nc: nc, r: bufio.NewReader(nc), w: bufio.NewWriter(nc)}
	if opts.Password != "" {
		args := []string{"AUTH", opts.Password}
		if opts.Username != "" {
			args = []string{"AUTH", opts.Username, opts.Password}
		}
		if _, err := c.do(ctx, 0, args...); err != nil {
			nc.Close()
			return nil, fmt.Errorf("redis auth: %w", err)
		}
	}
	return c, nil
}

func (c *conn) Close() error { return c.nc.Close() }

// do sends a command and reads the reply. extra is added to the context deadline
// for blocking commands such as XREADGROUP BLOCK.
func (c *conn) do(ctx context.Context, extra time.Duration, args ...string) (any, error) {
	deadline := time.Now().Add(10*time.Second + extra)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		deadline = d
	}
	_ = c.nc.SetDeadline(deadline)
	if err := writeCommand(c.w, args); err != nil {
		return nil, err
	}
	if err := c.w.Flush(); err != nil {
		return nil, err
	}
	reply, err := readReply(c.r)
	if err != nil {
		return nil, err
	}
	if rerr, ok := reply.(RedisError); ok {
		return nil, rerr
	}
	return reply, nil
}

func writeCommand(w *bufio.Writer, args []string) error {
	if _, err := fmt.Fprintf(w, "*%d\r\n", len(args)); err != nil {
		return err
	}
	for _, a := range args {
		if _, err := fmt.Fprintf(w, "$%d\r\n%s\r\n", len(a), a); err != nil {
			return err
		}
	}
	return nil
}

const maxBulk = 512 << 20

// readReply parses one RESP2 value: string, RedisError, int64, nil or []any.
func readReply(r *bufio.Reader) (any, error) {
	line, err := readLine(r)
	if err != nil {
		return nil, err
	}
	if len(line) == 0 {
		return nil, errors.New("redis: empty reply line")
	}
	body := string(line[1:])
	switch line[0] {
	case '+':
		return body, nil
	case '-':
		return RedisError(body), nil
	case ':':
		return strconv.ParseInt(body, 10, 64)
	case '$':
		n, err := strconv.Atoi(body)
		if err != nil || n > maxBulk {
			return nil, fmt.Errorf("redis: bad bulk length %q", body)
		}
		if n < 0 {
			return nil, nil
		}
		buf := make([]byte, n+2)
		if _, err := io.ReadFull(r, buf); err != nil {
			return nil, err
		}
		return string(buf[:n]), nil
	case '*':
		n, err := strconv.Atoi(body)
		if err != nil {
			return nil, fmt.Errorf("redis: bad array length %q", body)
		}
		if n < 0 {
			return nil, nil
		}
		items := make([]any, n)
		for i := range items {
			if items[i], err = readReply(r); err != nil {
				return nil, err
			}
		}
		return items, nil
	}
	return nil, fmt.Errorf("redis: unknown reply type %q", line[0])
}

func readLine(r *bufio.Reader) ([]byte, error) {
	line, err := r.ReadSlice('\n')
	if err != nil {
		return nil, err
	}
	if len(line) < 2 || line[len(line)-2] != '\r' {
		return nil, errors.New("redis: malformed line")
	}
	return line[:len(line)-2], nil
}
