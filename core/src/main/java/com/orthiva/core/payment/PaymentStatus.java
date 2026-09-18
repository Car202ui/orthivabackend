package com.orthiva.core.payment;

/** Mirrors the PostgreSQL enum {@code payment_status}. */
public enum PaymentStatus {
    PENDING, APPROVED, DECLINED, REFUNDED, ERROR
}
