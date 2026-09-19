package com.orthiva.core.notification.application;

import java.util.Locale;
import java.util.Map;

/** Port to the mail transport: renders a template in the recipient's language and sends it. */
public interface Mailer {

    /**
     * @param template name under templates/mail/ (e.g. "order-submitted"); its subject is the
     *                 message key {@code mail.<template>.subject}
     * @param model    variables for the template
     */
    void send(String to, String template, Locale locale, Map<String, Object> model);
}
