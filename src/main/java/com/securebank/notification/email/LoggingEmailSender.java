package com.securebank.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "securebank.email.sender-mode",
        havingValue = "logging",
        matchIfMissing = true
)
public class LoggingEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailNotification notification) {
        logger.info(
                "Prepared email notification: from={}, to={}, subject={}",
                notification.from(),
                notification.to(),
                notification.subject()
        );
    }
}
