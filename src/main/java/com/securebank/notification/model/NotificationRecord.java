package com.securebank.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_transfer_recipient_type",
                columnNames = {"transaction_id", "recipient_email", "notification_type"}
        )
)
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(nullable = false)
    private String subject;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected NotificationRecord() {
    }

    public NotificationRecord(
            Long transactionId,
            String recipientEmail,
            NotificationType type,
            String subject
    ) {
        this.transactionId = transactionId;
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.subject = subject;
        this.status = NotificationStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markSent(Instant sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.failedAt = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant failedAt) {
        this.status = NotificationStatus.FAILED;
        this.failedAt = failedAt;
        this.errorMessage = truncate(errorMessage, 1000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
