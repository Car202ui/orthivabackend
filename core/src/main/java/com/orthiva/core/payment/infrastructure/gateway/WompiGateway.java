package com.orthiva.core.payment.infrastructure.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.orthiva.core.payment.PaymentDto;
import com.orthiva.core.payment.PaymentStatus;
import com.orthiva.core.payment.application.gateway.PaymentGateway;
import com.orthiva.core.shared.web.DomainException;

/**
 * Wompi hosted checkout (web checkout by redirection) + event webhook.
 * <ul>
 *   <li>Checkout: {@code checkoutUrl?public-key&currency&amount-in-cents&reference&signature:integrity&redirect-url}
 *       where {@code signature:integrity = sha256(reference + amountInCents + currency + integritySecret)}.</li>
 *   <li>Webhook: {@code sha256(concat(values of signature.properties) + timestamp + eventsSecret)} must equal
 *       {@code signature.checksum} (also sent as header {@code X-Event-Checksum}).</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(WompiProperties.class)
class WompiGateway implements PaymentGateway {

    static final String NAME = "WOMPI";
    private static final Logger log = LoggerFactory.getLogger(WompiGateway.class);

    private final WompiProperties props;
    private final ObjectMapper json;

    WompiGateway(WompiProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        if (!props.configured()) {
            log.info("Wompi gateway disabled: set WOMPI_PUBLIC_KEY, WOMPI_INTEGRITY_SECRET and WOMPI_EVENTS_SECRET to enable it");
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return props.configured();
    }

    @Override
    public CheckoutSession createCheckout(PaymentDto payment, String returnUrl) {
        String reference = "orthiva-" + payment.id() + "-" + UUID.randomUUID().toString().substring(0, 8);
        long cents = payment.amount().movePointRight(2).longValueExact();
        String integrity = sha256(reference + cents + payment.currency() + props.integritySecret());
        String base = props.checkoutUrl() == null ? "https://checkout.wompi.co/p/" : props.checkoutUrl();
        String url = base + "?public-key=" + enc(props.publicKey())
                + "&currency=" + enc(payment.currency())
                + "&amount-in-cents=" + cents
                + "&reference=" + enc(reference)
                + "&signature:integrity=" + integrity
                + "&redirect-url=" + enc(returnUrl);
        return new CheckoutSession(reference, url);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WebhookResult handleWebhook(Map<String, String> headers, String rawBody) {
        Map<String, Object> event;
        try {
            event = json.readValue(rawBody, new TypeReference<>() { });
        } catch (Exception e) {
            throw DomainException.badRequest("invalid_webhook", "Malformed Wompi event");
        }
        var signature = (Map<String, Object>) event.get("signature");
        var data = (Map<String, Object>) event.get("data");
        if (signature == null || data == null || event.get("timestamp") == null) {
            throw DomainException.badRequest("invalid_webhook", "Wompi event without signature, data or timestamp");
        }
        var properties = (List<String>) signature.get("properties");
        var concat = new StringBuilder();
        for (String path : properties) {
            concat.append(valueAt(data, path));
        }
        concat.append(event.get("timestamp")).append(props.eventsSecret());
        String expected = sha256(concat.toString());
        String given = String.valueOf(signature.get("checksum"));
        String header = headers.getOrDefault("x-event-checksum", given);
        if (!expected.equalsIgnoreCase(given) || !expected.equalsIgnoreCase(header)) {
            throw DomainException.badRequest("invalid_signature", "Wompi event checksum does not match");
        }

        var tx = (Map<String, Object>) data.get("transaction");
        String reference = String.valueOf(tx.get("reference"));
        PaymentStatus status = switch (String.valueOf(tx.get("status"))) {
            case "APPROVED" -> PaymentStatus.APPROVED;
            case "DECLINED", "VOIDED" -> PaymentStatus.DECLINED;
            case "ERROR" -> PaymentStatus.ERROR;
            default -> PaymentStatus.PENDING;
        };
        return new WebhookResult(reference, status, event);
    }

    /** Dotted path inside the event's {@code data} ("transaction.amount_in_cents"). */
    @SuppressWarnings("unchecked")
    private static String valueAt(Map<String, Object> data, String path) {
        Object current = data;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return "";
            }
            current = ((Map<String, Object>) m).get(key);
        }
        return current == null ? "" : String.valueOf(current);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
