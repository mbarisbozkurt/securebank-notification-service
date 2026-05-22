package com.securebank.notification.service;

import com.securebank.notification.event.TransferCompletedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class TransferCompletedEventListener {

    private final TransferNotificationService transferNotificationService;

    public TransferCompletedEventListener(TransferNotificationService transferNotificationService) {
        this.transferNotificationService = transferNotificationService;
    }

    @RabbitListener(
            queues = "${securebank.rabbitmq.transfer-completed-queue}",
            autoStartup = "${securebank.rabbitmq.listener-auto-startup}"
    )
    public void handleTransferCompleted(TransferCompletedEvent event) {
        transferNotificationService.processTransferCompleted(event);
    }
}
