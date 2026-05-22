package com.securebank.notification.service;

import com.securebank.notification.event.TransferCompletedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransferCompletedEventListenerTests {

    @Test
    void delegatesTransferCompletedEventToNotificationService() {
        TransferNotificationService notificationService = mock(TransferNotificationService.class);
        TransferCompletedEventListener listener = new TransferCompletedEventListener(notificationService);
        TransferCompletedEvent event = new TransferCompletedEvent(
                1L,
                10L,
                20L,
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("125.50"),
                "TRY",
                "Test transfer",
                Instant.parse("2026-05-19T00:00:00Z")
        );

        listener.handleTransferCompleted(event);

        verify(notificationService).processTransferCompleted(event);
    }
}
