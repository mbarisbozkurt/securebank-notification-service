package com.securebank.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferCompletedEvent(
        Long transactionId,
        Long fromAccountId,
        Long toAccountId,
        String senderEmail,
        String recipientEmail,
        BigDecimal amount,
        String currency,
        String description,
        Instant occurredAt
) {
}
