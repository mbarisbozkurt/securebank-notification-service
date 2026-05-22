package com.securebank.notification.service;

import com.securebank.notification.email.EmailNotification;
import com.securebank.notification.email.EmailSender;
import com.securebank.notification.event.TransferCompletedEvent;
import com.securebank.notification.model.NotificationRecord;
import com.securebank.notification.model.NotificationStatus;
import com.securebank.notification.model.NotificationType;
import com.securebank.notification.repository.NotificationRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TransferNotificationServiceTests {

    @Test
    void buildsAndSendsTransferCompletedEmail() {
        EmailSender emailSender = mock(EmailSender.class);
        NotificationRecordRepository repository = notificationRecordRepository();
        TransferNotificationService service = new TransferNotificationService(
                emailSender,
                repository,
                "no-reply@securebank.local"
        );
        TransferCompletedEvent event = new TransferCompletedEvent(
                42L,
                100L,
                200L,
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("125.50"),
                "TRY",
                "Rent payment",
                Instant.parse("2026-05-19T12:30:00Z")
        );

        service.processTransferCompleted(event);

        ArgumentCaptor<EmailNotification> notificationCaptor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailSender, times(2)).send(notificationCaptor.capture());

        List<EmailNotification> notifications = notificationCaptor.getAllValues();
        EmailNotification senderNotification = notifications.get(0);
        EmailNotification recipientNotification = notifications.get(1);

        assertThat(senderNotification.from()).isEqualTo("no-reply@securebank.local");
        assertThat(senderNotification.to()).isEqualTo("sender@example.com");
        assertThat(senderNotification.subject()).isEqualTo("Your SecureBank transfer was completed");
        assertThat(senderNotification.textBody())
                .contains("Hello,")
                .contains("Your transfer of 125.50 TRY has been completed successfully.")
                .contains("Transaction details:")
                .contains("Transaction ID: 42")
                .contains("From account: ****100")
                .contains("To account: ****200")
                .contains("Amount: 125.50 TRY")
                .contains("Description: Rent payment")
                .contains("Completed at: May 19, 2026 12:30 UTC")
                .contains("If you did not authorize this transaction, please contact SecureBank support immediately.")
                .contains("SecureBank");
        assertThat(senderNotification.htmlBody())
                .contains("<strong>125.50 TRY</strong>")
                .contains("SecureBank")
                .contains("Transaction ID")
                .contains("****100")
                .contains("****200");

        assertThat(recipientNotification.from()).isEqualTo("no-reply@securebank.local");
        assertThat(recipientNotification.to()).isEqualTo("recipient@example.com");
        assertThat(recipientNotification.subject()).isEqualTo("You received a SecureBank transfer");
        assertThat(recipientNotification.textBody())
                .contains("Hello,")
                .contains("You received a transfer of 125.50 TRY.")
                .contains("Transaction details:")
                .contains("Transaction ID: 42")
                .contains("From account: ****100")
                .contains("To account: ****200")
                .contains("Amount: 125.50 TRY")
                .contains("Description: Rent payment")
                .contains("Completed at: May 19, 2026 12:30 UTC")
                .contains("If you do not recognize this transaction, please contact SecureBank support.")
                .contains("SecureBank");
        assertThat(recipientNotification.htmlBody())
                .contains("<strong>125.50 TRY</strong>")
                .contains("You received a transfer")
                .contains("If you do not recognize this transaction");

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(repository, times(4)).save(recordCaptor.capture());
        assertThat(recordCaptor.getAllValues())
                .filteredOn(record -> record.getStatus() == NotificationStatus.SENT)
                .extracting(NotificationRecord::getType)
                .contains(NotificationType.TRANSFER_SENT, NotificationType.TRANSFER_RECEIVED);
    }

    @Test
    void usesDashWhenTransferDescriptionIsMissing() {
        EmailSender emailSender = mock(EmailSender.class);
        NotificationRecordRepository repository = notificationRecordRepository();
        TransferNotificationService service = new TransferNotificationService(
                emailSender,
                repository,
                "no-reply@securebank.local"
        );
        TransferCompletedEvent event = new TransferCompletedEvent(
                43L,
                101L,
                201L,
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("25.00"),
                "TRY",
                null,
                Instant.parse("2026-05-19T13:30:00Z")
        );

        service.processTransferCompleted(event);

        ArgumentCaptor<EmailNotification> notificationCaptor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailSender, times(2)).send(notificationCaptor.capture());

        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.textBody()).contains("Description: -");
                    assertThat(notification.htmlBody()).contains("<td style=");
                });
    }

    @Test
    void sendsOnlyOneEmailWhenSenderAndRecipientEmailAreSame() {
        EmailSender emailSender = mock(EmailSender.class);
        NotificationRecordRepository repository = notificationRecordRepository();
        TransferNotificationService service = new TransferNotificationService(
                emailSender,
                repository,
                "no-reply@securebank.local"
        );
        TransferCompletedEvent event = new TransferCompletedEvent(
                44L,
                102L,
                202L,
                "same-user@example.com",
                "same-user@example.com",
                new BigDecimal("10.00"),
                "TRY",
                "Own account transfer",
                Instant.parse("2026-05-19T14:30:00Z")
        );

        service.processTransferCompleted(event);

        ArgumentCaptor<EmailNotification> notificationCaptor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailSender).send(notificationCaptor.capture());

        EmailNotification notification = notificationCaptor.getValue();
        assertThat(notification.to()).isEqualTo("same-user@example.com");
        assertThat(notification.subject()).isEqualTo("Your SecureBank transfer was completed");
        verify(repository, times(2)).save(any(NotificationRecord.class));
    }

    @Test
    void escapesTransferDescriptionInHtmlEmail() {
        EmailSender emailSender = mock(EmailSender.class);
        NotificationRecordRepository repository = notificationRecordRepository();
        TransferNotificationService service = new TransferNotificationService(
                emailSender,
                repository,
                "no-reply@securebank.local"
        );
        TransferCompletedEvent event = new TransferCompletedEvent(
                45L,
                103L,
                203L,
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("10.00"),
                "TRY",
                "<script>alert('x')</script>",
                Instant.parse("2026-05-19T15:30:00Z")
        );

        service.processTransferCompleted(event);

        ArgumentCaptor<EmailNotification> notificationCaptor = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailSender, times(2)).send(notificationCaptor.capture());

        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(notification -> assertThat(notification.htmlBody())
                        .doesNotContain("<script>")
                        .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
    }

    @Test
    void marksNotificationFailedAndRethrowsWhenEmailSendFails() {
        EmailSender emailSender = mock(EmailSender.class);
        NotificationRecordRepository repository = notificationRecordRepository();
        TransferNotificationService service = new TransferNotificationService(
                emailSender,
                repository,
                "no-reply@securebank.local"
        );
        TransferCompletedEvent event = new TransferCompletedEvent(
                46L,
                104L,
                204L,
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("10.00"),
                "TRY",
                "Failure test",
                Instant.parse("2026-05-19T16:30:00Z")
        );
        doThrow(new IllegalStateException("SMTP provider unavailable"))
                .when(emailSender)
                .send(any(EmailNotification.class));

        assertThatThrownBy(() -> service.processTransferCompleted(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMTP provider unavailable");

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(repository, times(2)).save(recordCaptor.capture());
        NotificationRecord failedRecord = recordCaptor.getAllValues().get(1);
        assertThat(failedRecord.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedRecord.getFailedAt()).isNotNull();
        assertThat(failedRecord.getErrorMessage()).isEqualTo("SMTP provider unavailable");
    }

    private NotificationRecordRepository notificationRecordRepository() {
        NotificationRecordRepository repository = mock(NotificationRecordRepository.class);
        when(repository.findByTransactionIdAndRecipientEmailAndType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }
}
