package queue

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
)

// Message is one stream entry.
type Message struct {
	ID     string
	Fields map[string]string
}

// Stream wraps the stream commands the judge needs on a single connection.
type Stream struct {
	opts Options
	c    *conn
}

func NewStream(opts Options) *Stream { return &Stream{opts: opts} }

func (s *Stream) Close() {
	if s.c != nil {
		_ = s.c.Close()
		s.c = nil
	}
}

func (s *Stream) do(ctx context.Context, extra time.Duration, args ...string) (any, error) {
	if s.c == nil {
		c, err := dial(ctx, s.opts)
		if err != nil {
			return nil, err
		}
		s.c = c
	}
	reply, err := s.c.do(ctx, extra, args...)
	var rerr RedisError
	if err != nil && !errors.As(err, &rerr) {
		// Network problem: drop the connection, the next call reconnects.
		s.Close()
	}
	return reply, err
}

func (s *Stream) Ping(ctx context.Context) error {
	_, err := s.do(ctx, 0, "PING")
	return err
}

// EnsureGroup creates the consumer group (and the stream) if needed.
func (s *Stream) EnsureGroup(ctx context.Context, stream, group string) error {
	_, err := s.do(ctx, 0, "XGROUP", "CREATE", stream, group, "$", "MKSTREAM")
	if err != nil && strings.HasPrefix(err.Error(), "BUSYGROUP") {
		return nil
	}
	return err
}

// Add appends an entry, trimming the stream to about maxLen entries.
func (s *Stream) Add(ctx context.Context, stream string, maxLen int, fields map[string]string) (string, error) {
	args := []string{"XADD", stream, "MAXLEN", "~", strconv.Itoa(maxLen), "*"}
	for k, v := range fields {
		args = append(args, k, v)
	}
	reply, err := s.do(ctx, 0, args...)
	if err != nil {
		return "", err
	}
	id, _ := reply.(string)
	return id, nil
}

// ReadGroup waits up to block for one new message for this consumer.
func (s *Stream) ReadGroup(ctx context.Context, stream, group, consumer string, block time.Duration) (*Message, error) {
	reply, err := s.do(ctx, block, "XREADGROUP", "GROUP", group, consumer, "COUNT", "1",
		"BLOCK", strconv.FormatInt(block.Milliseconds(), 10), "STREAMS", stream, ">")
	if err != nil || reply == nil {
		return nil, err
	}
	streams, ok := reply.([]any)
	if !ok || len(streams) == 0 {
		return nil, nil
	}
	pair, ok := streams[0].([]any)
	if !ok || len(pair) != 2 {
		return nil, fmt.Errorf("redis: unexpected XREADGROUP reply")
	}
	entries, _ := pair[1].([]any)
	if len(entries) == 0 {
		return nil, nil
	}
	return parseEntry(entries[0])
}

// Ack marks a message as processed.
func (s *Stream) Ack(ctx context.Context, stream, group, id string) error {
	_, err := s.do(ctx, 0, "XACK", stream, group, id)
	return err
}

// AutoClaim takes over one message that another consumer left pending for longer than minIdle.
func (s *Stream) AutoClaim(ctx context.Context, stream, group, consumer string, minIdle time.Duration) (*Message, error) {
	reply, err := s.do(ctx, 0, "XAUTOCLAIM", stream, group, consumer,
		strconv.FormatInt(minIdle.Milliseconds(), 10), "0-0", "COUNT", "1")
	if err != nil {
		return nil, err
	}
	parts, ok := reply.([]any)
	if !ok || len(parts) < 2 {
		return nil, fmt.Errorf("redis: unexpected XAUTOCLAIM reply")
	}
	entries, _ := parts[1].([]any)
	for _, e := range entries {
		if e == nil { // deleted entry
			continue
		}
		return parseEntry(e)
	}
	return nil, nil
}

// DeliveryCount returns how many times a pending message was delivered.
func (s *Stream) DeliveryCount(ctx context.Context, stream, group, id string) (int64, error) {
	reply, err := s.do(ctx, 0, "XPENDING", stream, group, id, id, "1")
	if err != nil {
		return 0, err
	}
	rows, _ := reply.([]any)
	if len(rows) == 0 {
		return 0, nil
	}
	row, _ := rows[0].([]any)
	if len(row) < 4 {
		return 0, fmt.Errorf("redis: unexpected XPENDING reply")
	}
	count, _ := row[3].(int64)
	return count, nil
}

func parseEntry(raw any) (*Message, error) {
	entry, ok := raw.([]any)
	if !ok || len(entry) != 2 {
		return nil, fmt.Errorf("redis: unexpected stream entry")
	}
	id, _ := entry[0].(string)
	values, _ := entry[1].([]any)
	msg := &Message{ID: id, Fields: make(map[string]string, len(values)/2)}
	for i := 0; i+1 < len(values); i += 2 {
		k, _ := values[i].(string)
		v, _ := values[i+1].(string)
		msg.Fields[k] = v
	}
	return msg, nil
}
