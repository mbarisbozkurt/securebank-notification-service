package com.securebank.notification.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTests {

    @Test
    void sendsHtmlEmailThroughJavaMailSender() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpEmailSender sender = new SmtpEmailSender(javaMailSender);
        EmailNotification notification = new EmailNotification(
                "no-reply@securebank.local",
                "customer@example.com",
                "Your SecureBank transfer was completed",
                "Transfer completed successfully.",
                "<strong>Transfer completed successfully.</strong>"
        );

        sender.send(notification);

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());

        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@securebank.local");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
        assertThat(message.getSubject()).isEqualTo("Your SecureBank transfer was completed");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        String rawMessage = output.toString(StandardCharsets.UTF_8);
        assertThat(rawMessage)
                .contains("multipart")
                .contains("text/plain")
                .contains("text/html")
                .contains("Transfer completed successfully.")
                .contains("<strong>Transfer completed successfully.</strong>");
    }
}
