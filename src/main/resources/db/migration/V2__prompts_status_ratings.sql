-- Prompts (tasks for the models) are stored in the database and written in the UI.
-- A prompt that was already used is never edited in place: a change creates a new prompt,
-- so old runs always point at the exact text the models received.
create table prompt (
    id         bigserial primary key,
    name       varchar(255) not null unique,
    text       text         not null,
    created_at timestamptz  not null default now()
);

-- Runs are executed in the background: status and progress.
alter table run
    add column status         varchar(16) not null default 'DONE'
        check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    add column expected_calls integer,
    add column error_message  text;

-- Link to the prompt plus a manual quality rating of the answer.
alter table llm_call
    add column prompt_id   bigint references prompt (id),
    add column rating      smallint check (rating between 1 and 5),
    add column rating_note text;

create index idx_llm_call_prompt on llm_call (prompt_id, provider, model);
