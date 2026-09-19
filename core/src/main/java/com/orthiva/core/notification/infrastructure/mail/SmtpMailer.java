package com.orthiva.core.notification.infrastructure.mail;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.orthiva.core.notification.application.Mailer;

/**
 * One Thymeleaf layout ({@code templates/mail/notice.html}) + texts from the Spring
 * MessageSource ({@code messages_es/en.properties}, keys {@code mail.<template>.subject/title/body})
 * → MIME e-mail through JavaMailSender. In development the SMTP host is Mailpit (UI at :8025).
 * A failing send is logged, never propagated: notifications must not break the business
 * transaction that produced the event.
 */
@Component
class SmtpMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    /** Positional arguments every text may use: {0} order, {1} doctor, {2} patient, {3} lab, {4} version, {5} amount, {6} month, {7} visit date, {8} purpose, {9} gateway, {10} recipient. */
    private static final String[] ARG_KEYS = { "orderNumber", "doctorName", "patientName", "labName", "version",
            "amount", "month", "visitDate", "purpose", "gateway", "recipientName" };

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final MessageSource messages;
    private final String from;
    private final String appUrl;

    SmtpMailer(JavaMailSender sender, TemplateEngine templates, MessageSource messages,
               @Value("${orthiva.mail.from}") String from, @Value("${orthiva.app-url}") String appUrl) {
        this.sender = sender;
        this.templates = templates;
        this.messages = messages;
        this.from = from;
        this.appUrl = appUrl;
    }

    @Override
    public void send(String to, String template, Locale locale, Map<String, Object> model) {
        try {
            Object[] args = new Object[ARG_KEYS.length];
            for (int i = 0; i < ARG_KEYS.length; i++) {
                Object v = model.get(ARG_KEYS[i]);
                args[i] = v == null ? "" : String.valueOf(v);
            }
            String subject = messages.getMessage("mail." + template + ".subject", args, locale);
            var ctx = new Context(locale);
            ctx.setVariable("title", messages.getMessage("mail." + template + ".title", args, locale));
            ctx.setVariable("body", messages.getMessage("mail." + template + ".body", args, locale));
            ctx.setVariable("cta", messages.getMessage("mail.cta", args, locale));
            ctx.setVariable("footer", messages.getMessage("mail.footer", args, locale));
            ctx.setVariable("labName", model.getOrDefault("labName", "Orthiva"));
            ctx.setVariable("link", appUrl + "/" + locale.getLanguage() + "/dashboard");
            String html = templates.process("mail/notice", ctx);

            var mime = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mime);
            log.info("Mail '{}' sent to {} ({})", template, to, locale.getLanguage());
        } catch (Exception e) {
            log.error("Mail '{}' to {} failed: {}", template, to, e.getMessage());
        }
    }
}
