-- Access reviews (P-028): rounds of checking that people still need the roles they hold. A review covers the role
-- assignments held, then or later, by the people in its scope when it started; the people who look after each person
-- decide Keep or Remove until it closes, and closing takes away the ones decided Remove.
create table access_reviews (
    id         uuid primary key,
    name       varchar(100) not null,
    -- Null: every role; otherwise the one role it covers
    role_id    uuid,
    -- Null: everyone; otherwise the branch whose people it covers
    branch_id  uuid,
    due_at     timestamptz  not null,
    status     text         not null,
    created_by uuid,
    created_at timestamptz  not null,
    closed_at  timestamptz,
    -- Null once closed: the server closed it at due_at
    closed_by  uuid,
    constraint ck_access_reviews_status check (status in ('OPEN', 'CLOSED'))
);
-- What AccessReviewJob looks for
create index ix_access_reviews_open_due on access_reviews (due_at) where status = 'OPEN';

-- People named to decide all of it, besides each person's administrators
create table access_review_reviewers (
    review_id uuid not null,
    user_id   uuid not null,
    primary key (review_id, user_id),
    constraint fk_access_review_reviewers_review foreign key (review_id) references access_reviews (id) on delete cascade
);

-- Each assignment as it was when the review started, kept as text, so the report outlives the person, the role and the
-- assignment: no foreign keys to them
create table access_review_items (
    id              uuid primary key,
    review_id       uuid         not null,
    assignment_id   uuid         not null,
    user_id         uuid         not null,
    user_name       text         not null,
    email           text         not null,
    role_id         uuid         not null,
    role_name       text         not null,
    branch_id       uuid,
    starts_at       timestamptz  not null,
    ends_at         timestamptz,
    decision        text,
    note            varchar(500) not null default '',
    decided_by      uuid,
    decided_by_name text         not null default '',
    decided_at      timestamptz,
    outcome         text,
    constraint fk_access_review_items_review foreign key (review_id) references access_reviews (id) on delete cascade,
    constraint ck_access_review_items_decision check (decision in ('KEEP', 'REMOVE')),
    constraint ck_access_review_items_outcome check (outcome in ('KEPT', 'REMOVED', 'NOT_REVIEWED', 'GONE', 'LAST_ADMINISTRATOR'))
);
create index ix_access_review_items_review_id on access_review_items (review_id);
