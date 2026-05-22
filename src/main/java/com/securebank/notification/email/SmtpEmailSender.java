package com.securebank.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(name = "securebank.email.sender-mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailNotification notification) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(notification.from());
            helper.setTo(notification.to());
            helper.setSubject(notification.subject());
            helper.setText(notification.textBody(), notification.htmlBody());

            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to build email notification", exception);
        }

        logger.info(
                "Sent email notification: from={}, to={}, subject={}",
                notification.from(),
                notification.to(),
                notification.subject()
        );
    }
}


    
    
        
    

    
