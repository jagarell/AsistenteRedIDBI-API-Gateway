package com.upc.idbi.gateway.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envío de correo real (recuperación de contraseña, propuestas técnicas).
 * Desactivado por defecto ({@code app.mail.enabled=false}): en ese caso
 * lanza {@link EmailDeliveryException} inmediatamente, sin intentar conectar
 * a ningún servidor SMTP. Se activa configurando las variables de entorno
 * MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD y MAIL_ENABLED=true.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.enabled}") boolean enabled
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    public void sendSimple(String to, String subject, String body) {
        requireEnabled();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Fallo al enviar correo simple (destinatario omitido del log): {}", e.getMessage());
            throw new EmailDeliveryException("No se pudo enviar el correo", e);
        }
    }

    public void sendWithAttachment(
            String to,
            String cc,
            String subject,
            String body,
            byte[] attachmentBytes,
            String attachmentFilename
    ) {
        requireEnabled();
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            if (cc != null && !cc.isBlank()) {
                helper.setCc(cc);
            }
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes), "application/pdf");

            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException e) {
            log.warn("Fallo al enviar correo con adjunto (destinatario omitido del log): {}", e.getMessage());
            throw new EmailDeliveryException("No se pudo enviar el correo", e);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new EmailDeliveryException(
                    "El envío de correo no está habilitado en este entorno " +
                            "(configura MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD y MAIL_ENABLED=true)"
            );
        }
    }
}
