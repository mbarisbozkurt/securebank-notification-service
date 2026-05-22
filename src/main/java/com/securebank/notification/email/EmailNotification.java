package com.securebank.notification.email;

public record EmailNotification(
        String from,
        String to,
        String subject,
        String textBody,
        String htmlBody
) {
}
