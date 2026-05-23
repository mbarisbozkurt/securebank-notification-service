# SecureBank Notification Service

SecureBank Notification Service is a separate Spring Boot service that consumes transfer events from RabbitMQ and sends transactional email notifications.

It is part of the SecureBank system, but it runs independently from the main banking backend. The backend publishes transfer events; this service owns email delivery, retry handling, dead-letter queue behavior, and notification status persistence.

## Responsibilities

- Consume `TransferCompletedEvent` messages from RabbitMQ.
- Send transfer-completed email to the sender.
- Send transfer-received email to the recipient.
- Avoid duplicate email when sender and recipient email addresses are the same.
- Support local logging mode and real SMTP mode.
- Persist notification status as `PENDING`, `SENT`, or `FAILED`.
- Retry failed message processing.
- Route exhausted failures to a dead-letter queue.

## Architecture

```text
securebank-backend
  -> RabbitMQ exchange: securebank.events
     -> queue: securebank.transfer.completed
        -> securebank-notification-service
           -> SMTP provider
           -> notifications table
```

In local Docker Compose, the service shares the PostgreSQL instance with the backend, but manages its own notification schema with Flyway.

## Message Flow

```text
TransferCompletedEvent received
  -> build sender and recipient emails
  -> create or reuse notification records
  -> send email
  -> mark records as SENT
```

If email sending fails:

```text
email send fails
  -> mark notification record as FAILED
  -> rethrow exception
  -> RabbitMQ retry runs
  -> exhausted message is rejected
  -> message goes to DLQ
```

## RabbitMQ Topology

```text
Main exchange: securebank.events
Main queue: securebank.transfer.completed
Routing key: transfer.completed

Dead-letter exchange: securebank.events.dlx
Dead-letter queue: securebank.transfer.completed.dlq
Dead-letter routing key: transfer.completed.dlq
```

Default retry settings:

```text
Max attempts: 3
Initial interval: 2000 ms
Multiplier: 2.0
Max interval: 10000 ms
```

## Email Delivery

Two sender modes are supported:

```text
MAIL_SENDER_MODE=logging
```

Logs email metadata only. Useful for local development without SMTP credentials.

```text
MAIL_SENDER_MODE=smtp
```

Sends multipart plain-text + HTML email through an SMTP provider.

Required SMTP settings:

```text
MAIL_FROM=no-reply@securebank.local
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your_smtp_username
MAIL_PASSWORD=your_smtp_password
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
```

## Security Notes

- RabbitMQ, database, and SMTP credentials are supplied through environment variables.
- SMTP credentials must not be committed.
- Logging mode logs email metadata only, not full email body.
- Account identifiers in email content are masked.
- Dynamic values in HTML email templates are escaped before rendering.
- Failed notification records keep error details for debugging without exposing SMTP secrets.

## Run With Docker Compose

The service is started from the backend repository's Docker Compose file:

```powershell
cd ..\securebank-backend
docker compose up -d --build
```

In the AWS deployment, this service runs on the same EC2 Docker Compose stack as the backend and consumes RabbitMQ events privately inside the Docker network.

Useful checks:

```powershell
docker compose logs -f notification-service
docker compose ps
```

Expected RabbitMQ state after successful processing:

```text
securebank.transfer.completed      Ready: 0  Unacked: 0
securebank.transfer.completed.dlq  Ready: 0  Unacked: 0
```

## Tests

```powershell
.\mvnw.cmd test
```

## Related Project

- `securebank-backend`: Main banking backend and Docker Compose entrypoint.
