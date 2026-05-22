package com.securebank.notification.service;

import com.securebank.notification.email.EmailNotification;
import com.securebank.notification.email.EmailSender;
import com.securebank.notification.event.TransferCompletedEvent;
import com.securebank.notification.model.NotificationRecord;
import com.securebank.notification.model.NotificationType;
import com.securebank.notification.repository.NotificationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TransferNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TransferNotificationService.class);
    private static final DateTimeFormatter EMAIL_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("MMMM d, yyyy HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final EmailSender emailSender;
    private final NotificationRecordRepository notificationRecordRepository;
    private final String emailFrom;

    public TransferNotificationService(
            EmailSender emailSender,
            NotificationRecordRepository notificationRecordRepository,
            @Value("${securebank.email.from}") String emailFrom
    ) {
        this.emailSender = emailSender;
        this.notificationRecordRepository = notificationRecordRepository;
        this.emailFrom = emailFrom;
    }

    public void processTransferCompleted(TransferCompletedEvent event) {
        List<TransferEmail> notifications = buildTransferCompletedEmails(event);
        notifications.forEach(notification -> sendAndTrack(event, notification));

        logger.info(
                "Processed transfer notification emails: transactionId={}, fromAccountId={}, toAccountId={}, amount={}, currency={}, recipients={}",
                event.transactionId(),
                event.fromAccountId(),
                event.toAccountId(),
                event.amount(),
                event.currency(),
                notifications.stream().map(notification -> notification.email().to()).toList()
        );
    }

    private void sendAndTrack(TransferCompletedEvent event, TransferEmail transferEmail) {
        EmailNotification email = transferEmail.email();
        NotificationRecord record = notificationRecordRepository
                .findByTransactionIdAndRecipientEmailAndType(event.transactionId(), email.to(), transferEmail.type())
                .orElseGet(() -> notificationRecordRepository.save(new NotificationRecord(
                        event.transactionId(),
                        email.to(),
                        transferEmail.type(),
                        email.subject()
                )));

        try {
            emailSender.send(email);
            record.markSent(Instant.now());
            notificationRecordRepository.save(record);
        } catch (RuntimeException ex) {
            record.markFailed(ex.getMessage(), Instant.now());
            notificationRecordRepository.save(record);
            throw ex;
        }
    }

    private List<TransferEmail> buildTransferCompletedEmails(TransferCompletedEvent event) {
        List<TransferEmail> notifications = new ArrayList<>();
        notifications.add(new TransferEmail(
                buildSenderTransferCompletedEmail(event),
                NotificationType.TRANSFER_SENT
        ));

        if (!event.recipientEmail().equalsIgnoreCase(event.senderEmail())) {
            notifications.add(new TransferEmail(
                    buildRecipientTransferReceivedEmail(event),
                    NotificationType.TRANSFER_RECEIVED
            ));
        }

        return notifications;
    }

    private EmailNotification buildSenderTransferCompletedEmail(TransferCompletedEvent event) {
        String subject = "Your SecureBank transfer was completed";
        String textBody = """
                Hello,

                Your transfer of %s %s has been completed successfully.

                Transaction details:
                Transaction ID: %d
                From account: %s
                To account: %s
                Amount: %s %s
                Description: %s
                Completed at: %s

                If you did not authorize this transaction, please contact SecureBank support immediately.

                SecureBank
                """.formatted(
                event.amount(),
                event.currency(),
                event.transactionId(),
                maskAccountId(event.fromAccountId()),
                maskAccountId(event.toAccountId()),
                event.amount(),
                event.currency(),
                valueOrDash(event.description()),
                EMAIL_DATE_FORMATTER.format(event.occurredAt())
        );

        String htmlBody = buildHtmlEmail(
                "Your transfer was completed",
                "Your transfer of <strong>%s %s</strong> has been completed successfully.".formatted(
                        escapeHtml(event.amount().toPlainString()),
                        escapeHtml(event.currency())
                ),
                transferDetails(event),
                "If you did not authorize this transaction, please contact SecureBank support immediately."
        );

        return new EmailNotification(
                emailFrom,
                event.senderEmail(),
                subject,
                textBody,
                htmlBody
        );
    }

    private EmailNotification buildRecipientTransferReceivedEmail(TransferCompletedEvent event) {
        String subject = "You received a SecureBank transfer";
        String textBody = """
                Hello,

                You received a transfer of %s %s.

                Transaction details:
                Transaction ID: %d
                From account: %s
                To account: %s
                Amount: %s %s
                Description: %s
                Completed at: %s

                If you do not recognize this transaction, please contact SecureBank support.

                SecureBank
                """.formatted(
                event.amount(),
                event.currency(),
                event.transactionId(),
                maskAccountId(event.fromAccountId()),
                maskAccountId(event.toAccountId()),
                event.amount(),
                event.currency(),
                valueOrDash(event.description()),
                EMAIL_DATE_FORMATTER.format(event.occurredAt())
        );

        String htmlBody = buildHtmlEmail(
                "You received a transfer",
                "You received a transfer of <strong>%s %s</strong>.".formatted(
                        escapeHtml(event.amount().toPlainString()),
                        escapeHtml(event.currency())
                ),
                transferDetails(event),
                "If you do not recognize this transaction, please contact SecureBank support."
        );

        return new EmailNotification(
                emailFrom,
                event.recipientEmail(),
                subject,
                textBody,
                htmlBody
        );
    }

    private String buildHtmlEmail(
            String title,
            String summary,
            Map<String, String> details,
            String securityNotice
    ) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;">
                          <tr>
                            <td style="padding:22px 28px;background:#0f766e;color:#ffffff;">
                              <div style="font-size:20px;font-weight:700;letter-spacing:0;">SecureBank</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <h1 style="margin:0 0 12px;font-size:22px;line-height:1.3;color:#111827;">%s</h1>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#374151;">%s</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;border-top:1px solid #e5e7eb;">
                                %s
                              </table>
                              <p style="margin:24px 0 0;padding:14px 16px;background:#fff7ed;border-left:4px solid #f97316;font-size:13px;line-height:1.5;color:#7c2d12;">%s</p>
                              <p style="margin:22px 0 0;font-size:13px;color:#6b7280;">This is an automated notification from SecureBank.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(title),
                summary,
                buildHtmlRows(details),
                escapeHtml(securityNotice)
        );
    }

    private String buildHtmlRows(Map<String, String> details) {
        StringBuilder rows = new StringBuilder();
        details.forEach((label, value) -> rows.append("""
                <tr>
                  <td style="padding:12px 0;border-bottom:1px solid #e5e7eb;font-size:13px;color:#6b7280;width:38%%;">%s</td>
                  <td style="padding:12px 0;border-bottom:1px solid #e5e7eb;font-size:14px;color:#111827;font-weight:600;text-align:right;">%s</td>
                </tr>
                """.formatted(escapeHtml(label), escapeHtml(value))));
        return rows.toString();
    }

    private Map<String, String> transferDetails(TransferCompletedEvent event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Transaction ID", String.valueOf(event.transactionId()));
        details.put("From account", maskAccountId(event.fromAccountId()));
        details.put("To account", maskAccountId(event.toAccountId()));
        details.put("Amount", "%s %s".formatted(event.amount(), event.currency()));
        details.put("Description", valueOrDash(event.description()));
        details.put("Completed at", EMAIL_DATE_FORMATTER.format(event.occurredAt()));
        return details;
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String maskAccountId(Long accountId) {
        String value = String.valueOf(accountId);
        if (value.length() <= 4) {
            return "****" + value;
        }
        return "****" + value.substring(value.length() - 4);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record TransferEmail(EmailNotification email, NotificationType type) {
    }
}
