package com.securebank.notification.repository;

import com.securebank.notification.model.NotificationRecord;
import com.securebank.notification.model.NotificationStatus;
import com.securebank.notification.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.table=flyway_schema_history_notification_test"
})
class NotificationRecordRepositoryTests {

    @Autowired
    private NotificationRecordRepository repository;

    @Test
    void savesPendingNotificationAndFindsByStatus() {
        NotificationRecord record = new NotificationRecord(
                42L,
                "customer@example.com",
                NotificationType.TRANSFER_SENT,
                "Your SecureBank transfer was completed"
        );

        NotificationRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(repository.findByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING))
                .extracting(NotificationRecord::getRecipientEmail)
                .containsExactly("customer@example.com");
    }

    @Test
    void marksNotificationAsSent() {
        NotificationRecord record = repository.saveAndFlush(new NotificationRecord(
                43L,
                "customer@example.com",
                NotificationType.TRANSFER_RECEIVED,
                "You received a SecureBank transfer"
        ));
        Instant sentAt = Instant.parse("2026-05-21T10:00:00Z");

        record.markSent(sentAt);
        NotificationRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getSentAt()).isEqualTo(sentAt);
        assertThat(saved.getFailedAt()).isNull();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void marksNotificationAsFailedAndTruncatesLongErrorMessage() {
        NotificationRecord record = repository.saveAndFlush(new NotificationRecord(
                44L,
                "customer@example.com",
                NotificationType.TRANSFER_SENT,
                "Your SecureBank transfer was completed"
        ));
        Instant failedAt = Instant.parse("2026-05-21T10:05:00Z");

        record.markFailed("x".repeat(1200), failedAt);
        NotificationRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getFailedAt()).isEqualTo(failedAt);
        assertThat(saved.getErrorMessage()).hasSize(1000);
    }
}
