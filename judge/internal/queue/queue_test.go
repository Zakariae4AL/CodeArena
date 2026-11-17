package queue

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

func TestReadReply(t *testing.T) {
	raw := "*3\r\n$5\r\nhello\r\n:42\r\n*2\r\n$-1\r\n+OK\r\n"
	got, err := readReply(bufio.NewReader(strings.NewReader(raw)))
	if err != nil {
		t.Fatal(err)
	}
	want := []any{"hello", int64(42), []any{nil, "OK"}}
	if fmt.Sprint(got) != fmt.Sprint(want) {
		t.Fatalf("got %#v", got)
	}
	errReply, _ := readReply(bufio.NewReader(strings.NewReader("-BUSYGROUP exists\r\n")))
	if _, ok := errReply.(RedisError); !ok {
		t.Fatalf("expected a RedisError, got %#v", errReply)
	}
	if _, err := readReply(bufio.NewReader(strings.NewReader("$99999999999\r\n"))); err == nil {
		t.Fatal("expected an error for a huge bulk string")
	}
}

// The tests below need a Redis server: REDIS_ADDR=localhost:6379 go test ./...
func redisOpts(t *testing.T) Options {
	addr := os.Getenv("REDIS_ADDR")
	if addr == "" {
		t.Skip("REDIS_ADDR not set")
	}
	return Options{Addr: addr, Password: os.Getenv("REDIS_PASSWORD")}
}

func uniqueName(prefix string) string {
	return fmt.Sprintf("test:%s:%d", prefix, time.Now().UnixNano())
}

func TestStreamRoundTrip(t *testing.T) {
	opts := redisOpts(t)
	ctx := context.Background()
	s := NewStream(opts)
	defer s.Close()
	stream := uniqueName("jobs")

	if err := s.EnsureGroup(ctx, stream, "judges"); err != nil {
		t.Fatal(err)
	}
	if err := s.EnsureGroup(ctx, stream, "judges"); err != nil {
		t.Fatalf("EnsureGroup must be idempotent: %v", err)
	}
	if _, err := s.Add(ctx, stream, 100, map[string]string{"payload": `{"a":"b c"}`}); err != nil {
		t.Fatal(err)
	}
	msg, err := s.ReadGroup(ctx, stream, "judges", "w1", time.Second)
	if err != nil || msg == nil || msg.Fields["payload"] != `{"a":"b c"}` {
		t.Fatalf("read: %v %+v", err, msg)
	}
	// Nothing else to read: the call blocks then returns nil.
	if again, err := s.ReadGroup(ctx, stream, "judges", "w1", 200*time.Millisecond); err != nil || again != nil {
		t.Fatalf("expected no message, got %+v %v", again, err)
	}
	// Not acknowledged yet: another consumer can claim it once it is idle.
	time.Sleep(60 * time.Millisecond)
	claimed, err := s.AutoClaim(ctx, stream, "judges", "w2", 50*time.Millisecond)
	if err != nil || claimed == nil || claimed.ID != msg.ID {
		t.Fatalf("claim: %v %+v", err, claimed)
	}
	if n, err := s.DeliveryCount(ctx, stream, "judges", msg.ID); err != nil || n != 2 {
		t.Fatalf("delivery count: %d %v", n, err)
	}
	if err := s.Ack(ctx, stream, "judges", msg.ID); err != nil {
		t.Fatal(err)
	}
	if none, err := s.AutoClaim(ctx, stream, "judges", "w3", 0); err != nil || none != nil {
		t.Fatalf("acknowledged message was claimed again: %+v %v", none, err)
	}
}

func TestConsumerJudgesJobsAndRecoversAbandonedOnes(t *testing.T) {
	opts := redisOpts(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	jobs, results := uniqueName("jobs"), uniqueName("results")

	producer := NewStream(opts)
	defer producer.Close()
	if err := producer.EnsureGroup(ctx, jobs, "judges"); err != nil {
		t.Fatal(err)
	}
	if err := producer.EnsureGroup(ctx, results, "core"); err != nil {
		t.Fatal(err)
	}
	// A job read by a worker that then "crashed" (never acknowledged).
	addJob(t, producer, jobs, "abandoned")
	if m, _ := producer.ReadGroup(ctx, jobs, "judges", "dead-worker", time.Second); m == nil {
		t.Fatal("setup failed")
	}
	addJob(t, producer, jobs, "fresh")
	time.Sleep(50 * time.Millisecond) // the abandoned job is now idle for longer than ClaimIdle

	consumer := &Consumer{
		Redis: opts, JobsStream: jobs, ResultsStream: results, Group: "judges", Name: "live-worker",
		Concurrency: 1, ClaimIdle: 10 * time.Millisecond, MaxDeliveries: 5,
		Log: slog.New(slog.NewTextHandler(io.Discard, nil)),
		Handle: func(ctx context.Context, job protocol.Job, pub *Publisher) error {
			return pub.Publish(ctx, protocol.Result{SubmissionID: job.SubmissionID, Type: protocol.ResultFinal, Verdict: protocol.Accepted})
		},
	}
	go func() { _ = consumer.Run(ctx) }()

	seen := map[string]bool{}
	deadline := time.Now().Add(10 * time.Second)
	for len(seen) < 2 && time.Now().Before(deadline) {
		msg, err := producer.ReadGroup(ctx, results, "core", "test", time.Second)
		if err != nil {
			t.Fatal(err)
		}
		if msg != nil {
			var r protocol.Result
			_ = json.Unmarshal([]byte(msg.Fields["payload"]), &r)
			seen[r.SubmissionID] = true
		}
	}
	if !seen["abandoned"] || !seen["fresh"] {
		t.Fatalf("results for %v", seen)
	}
}

func addJob(t *testing.T, s *Stream, stream, id string) {
	t.Helper()
	payload, _ := json.Marshal(protocol.Job{SubmissionID: id})
	if _, err := s.Add(context.Background(), stream, 100, map[string]string{"payload": string(payload)}); err != nil {
		t.Fatal(err)
	}
}
