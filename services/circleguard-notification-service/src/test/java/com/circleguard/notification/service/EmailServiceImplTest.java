package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private AuditLogService auditLog;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        auditLog = mock(AuditLogService.class);
        service = new EmailServiceImpl(mailSender, auditLog);
    }

    @Test
    void sendAsync_happyPath_sendsMailAndAuditsSuccess() throws Exception {
        CompletableFuture<Void> future = service.sendAsync("anon-99", "you may have been exposed");

        future.get();
        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage sent = mailCaptor.getValue();
        assertArrayEquals(new String[]{"anon-99@example.com"}, sent.getTo());
        assertTrue(sent.getText().contains("you may have been exposed"));

        verify(auditLog).logDelivery(eq("anon-99"), eq("EMAIL"), eq("SUCCESS"), any());
    }

    @Test
    void sendAsync_mailFails_auditsRetryAndPropagatesException() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        // The exception must propagate so Spring's @Retryable can retry. In a plain
        // unit test (no proxy), retry is not active, so we observe the direct throw.
        MailSendException ex = assertThrows(MailSendException.class,
                () -> service.sendAsync("anon-7", "hello"));
        assertEquals("smtp down", ex.getMessage());

        verify(auditLog).logDelivery(eq("anon-7"), eq("EMAIL"), eq("RETRY"), any());
    }

    @Test
    void recover_logsFailedAndReturnsFailedFuture() {
        CompletableFuture<Void> recovered = service.recover(
                new RuntimeException("upstream gone"), "anon-x", "msg");

        assertTrue(recovered.isCompletedExceptionally());
        verify(auditLog).logDelivery(eq("anon-x"), eq("EMAIL"), eq("FAILED"), eq(null));
    }
}
