// The judge worker: takes jobs from Redis, runs them in Docker sandboxes and
// streams verdicts back to the core.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/Zakariae4AL/CodeArena/judge/internal/protocol"
	"github.com/Zakariae4AL/CodeArena/judge/internal/queue"
	"github.com/Zakariae4AL/CodeArena/judge/internal/sandbox"
	"github.com/Zakariae4AL/CodeArena/judge/internal/worker"
)

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if v, err := strconv.Atoi(os.Getenv(key)); err == nil {
		return v
	}
	return fallback
}

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	hostname, _ := os.Hostname()

	redisOpts := queue.Options{
		Addr:     env("REDIS_ADDR", "localhost:6379"),
		Username: os.Getenv("REDIS_USERNAME"),
		Password: os.Getenv("REDIS_PASSWORD"),
		TLS:      os.Getenv("REDIS_TLS") == "true",
	}
	box := sandbox.NewDocker(sandbox.Config{
		ImagePrefix: env("JUDGE_IMAGE_PREFIX", "codearena/runner-"),
		Runtime:     os.Getenv("JUDGE_DOCKER_RUNTIME"),
		CPUs:        env("JUDGE_CPUS", "1"),
		PidsLimit:   envInt("JUDGE_PIDS_LIMIT", 128),
	})

	consumer := &queue.Consumer{
		Redis:         redisOpts,
		JobsStream:    env("JUDGE_JOBS_STREAM", "codearena:judge:jobs"),
		ResultsStream: env("JUDGE_RESULTS_STREAM", "codearena:judge:results"),
		Group:         env("JUDGE_GROUP", "judges"),
		Name:          env("JUDGE_NAME", fmt.Sprintf("%s-%d", hostname, os.Getpid())),
		Concurrency:   envInt("JUDGE_CONCURRENCY", 2),
		ClaimIdle:     time.Duration(envInt("JUDGE_CLAIM_IDLE_SECONDS", 180)) * time.Second,
		MaxDeliveries: int64(envInt("JUDGE_MAX_DELIVERIES", 3)),
		Log:           log,
		Handle: func(ctx context.Context, job protocol.Job, pub *queue.Publisher) error {
			w := worker.Worker{Sandbox: box, Publisher: pub, Log: log}
			return w.Process(ctx, job)
		},
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer stop()

	go serveHealth(ctx, env("JUDGE_HEALTH_ADDR", ":8081"), redisOpts, log)

	log.Info("judge started", "name", consumer.Name, "concurrency", consumer.Concurrency, "redis", redisOpts.Addr)
	if err := consumer.Run(ctx); err != nil && !errors.Is(err, context.Canceled) {
		log.Error("judge stopped", "error", err)
		os.Exit(1)
	}
	log.Info("judge stopped")
}

// serveHealth answers /healthz with 200 when Redis is reachable.
func serveHealth(ctx context.Context, addr string, opts queue.Options, log *slog.Logger) {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		s := queue.NewStream(opts)
		defer s.Close()
		if err := s.Ping(r.Context()); err != nil {
			http.Error(w, "redis unreachable", http.StatusServiceUnavailable)
			return
		}
		_, _ = w.Write([]byte("ok"))
	})
	srv := &http.Server{Addr: addr, Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	go func() {
		<-ctx.Done()
		_ = srv.Close()
	}()
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Warn("health server stopped", "error", err)
	}
}
