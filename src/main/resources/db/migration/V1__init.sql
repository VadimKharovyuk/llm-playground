-- One benchmark run: which models, with which parameters.
create table run (
    id              bigserial primary key,
    started_at      timestamptz      not null,
    finished_at     timestamptz,
    models          text             not null,   -- comma-separated "provider:model"
    prompts         text,                        -- comma-separated prompt names
    temperature     double precision,
    max_tokens      integer,
    runs_per_prompt integer          not null,
    streaming       boolean          not null,
    app_version     varchar(64),
    comment         text
);

-- One LLM call inside a run. Metrics and the answer live in one table;
-- the answer is a separate text column so report queries never select it.
create table llm_call (
    id                      bigserial primary key,
    run_id                  bigint       not null references run (id) on delete cascade,
    provider                varchar(32)  not null,
    model                   varchar(255) not null,
    prompt_name             varchar(255) not null,
    repeat_no               integer      not null,   -- 0 = warm-up call
    started_at              timestamptz  not null,
    duration_ms             bigint       not null,
    ttft_ms                 bigint,                  -- time to first token, streaming only
    prompt_tokens           integer,
    completion_tokens       integer,
    total_tokens            integer,
    tokens_per_second       double precision,
    finish_reason           varchar(64),
    cost_usd                numeric(18, 10),         -- OpenRouter reports it, Ollama is 0
    cold_start              boolean      not null default false,
    status                  varchar(16)  not null check (status in ('OK', 'ERROR', 'EMPTY')),
    error_message           text,
    response_id             varchar(255),
    -- Ollama's own metrics (null for other providers)
    eval_count              integer,
    eval_duration_ns        bigint,
    prompt_eval_count       integer,
    prompt_eval_duration_ns bigint,
    load_duration_ns        bigint,
    response_text           text
);

create index idx_llm_call_run on llm_call (run_id, provider, model);
