package queue

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
)

// Handler judges one job and publishes its results with the given publisher.
type Handler func(ctx context.Context, job protocol.Job, pub *Publisher) error

// Consumer reads jobs from a stream with a consumer group, so several judge
// workers can share the load and a crashed worker's jobs are picked up again.
type Consumer struct {
	Redis         Options
	JobsStream    string
	ResultsStream string
	Group         string
	Name          string
	Concurrency   int
	// Jobs pending longer than ClaimIdle on a dead consumer are taken over.
	ClaimIdle time.Duration
	// A job delivered more than MaxDeliveries times is given up (poison message).
	MaxDeliveries int64
	Handle        Handler
	Log           *slog.Logger
}

// Run blocks until ctx is cancelled and all running jobs are finished.
func (c *Consumer) Run(ctx context.Context) error {
	setup := NewStream(c.Redis)
	defer setup.Close()
	for {
		err := setup.EnsureGroup(ctx, c.JobsStream, c.Group)
		if err == nil {
			break
		}
		c.Log.Warn("waiting for redis", "error", err)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(2 * time.Second):
		}
	}

	var wg sync.WaitGroup
	for i := range max(c.Concurrency, 1) {
		wg.Go(func() { c.loop(ctx, i) })
	}
	wg.Wait()
	return nil
}

func (c *Consumer) loop(ctx context.Context, slot int) {
	s := NewStream(c.Redis)
	defer s.Close()
	pub := &Publisher{stream: s, name: c.ResultsStream}
	consumer := c.Name
	log := c.Log.With("slot", slot)
	nextClaim := time.Now()

	for ctx.Err() == nil {
		var msg *Message
		var err error
		claimed := false
		if time.Now().After(nextClaim) {
			msg, err = s.AutoClaim(ctx, c.JobsStream, c.Group, consumer, c.ClaimIdle)
			claimed = msg != nil
			if msg == nil {
				nextClaim = time.Now().Add(30 * time.Second)
			}
		}
		if err == nil && msg == nil {
			msg, err = s.ReadGroup(ctx, c.JobsStream, c.Group, consumer, 5*time.Second)
		}
		if err != nil {
			if ctx.Err() == nil {
				log.Warn("redis error, retrying", "error", err)
				time.Sleep(time.Second)
			}
			continue
		}
		if msg == nil {
			continue
		}
		// A running job finishes even if the worker is asked to stop.
		c.handle(context.WithoutCancel(ctx), s, pub, msg, claimed, log)
	}
}

func (c *Consumer) handle(ctx context.Context, s *Stream, pub *Publisher, msg *Message, claimed bool, log *slog.Logger) {
	var job protocol.Job
	if err := json.Unmarshal([]byte(msg.Fields["payload"]), &job); err != nil {
		log.Error("dropping malformed job", "id", msg.ID, "error", err)
		_ = s.Ack(ctx, c.JobsStream, c.Group, msg.ID)
		return
	}
	if claimed && c.MaxDeliveries > 0 {
		if n, err := s.DeliveryCount(ctx, c.JobsStream, c.Group, msg.ID); err == nil && n > c.MaxDeliveries {
			log.Error("giving up on job", "submission", job.SubmissionID, "deliveries", n)
			if err := pub.Publish(ctx, protocol.Result{SubmissionID: job.SubmissionID, Type: protocol.ResultFinal,
				Verdict: protocol.SystemError, Message: "The judge failed several times on this submission."}); err == nil {
				_ = s.Ack(ctx, c.JobsStream, c.Group, msg.ID)
			}
			return
		}
	}
	if err := c.Handle(ctx, job, pub); err != nil {
		// Not acknowledged: another attempt will claim it after ClaimIdle.
		log.Error("could not publish results", "submission", job.SubmissionID, "error", err)
		return
	}
	if err := s.Ack(ctx, c.JobsStream, c.Group, msg.ID); err != nil {
		log.Warn("ack failed", "id", msg.ID, "error", err)
	}
}

// Publisher writes results to the results stream.
type Publisher struct {
	stream *Stream
	name   string
}

func (p *Publisher) Publish(ctx context.Context, result protocol.Result) error {
	payload, err := json.Marshal(result)
	if err != nil {
		return err
	}
	_, err = p.stream.Add(ctx, p.name, 100_000, map[string]string{"payload": string(payload)})
	return err
}
