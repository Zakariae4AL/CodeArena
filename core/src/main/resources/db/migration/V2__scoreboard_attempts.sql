-- The scoreboard now stores every judged attempt instead of a counter per cell.
-- Wrong tries are counted by submission time, so verdicts can arrive in any order,
-- and the submission id as primary key makes a redelivered verdict harmless.
create table scoreboard_attempts (
    submission_id uuid    primary key,
    contest_id    bigint  not null,
    user_id       bigint  not null,
    problem_id    bigint  not null,
    accepted      boolean not null,
    submitted_at  timestamp(6) with time zone not null
);
create index ix_scoreboard_attempts_contest on scoreboard_attempts (contest_id);

drop table scoreboard_cells;
