create table notifications (
    id bigserial primary key,
    transaction_id bigint not null,
    recipient_email varchar(255) not null,
    notification_type varchar(32) not null,
    status varchar(32) not null,
    subject varchar(255) not null,
    error_message varchar(1000),
    created_at timestamp with time zone not null,
    sent_at timestamp with time zone,
    failed_at timestamp with time zone,
    constraint uk_notifications_transfer_recipient_type unique (transaction_id, recipient_email, notification_type)
);

create index idx_notifications_status_created_at on notifications (status, created_at);
create index idx_notifications_transaction_id on notifications (transaction_id);
