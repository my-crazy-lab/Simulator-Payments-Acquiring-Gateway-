package com.paymentgateway.shared.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.Map;

/**
 * Distributed tracing context manager using OpenTelemetry
 */
public class TracingContext {
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("payment-gateway");

    public static Span startSpan(String operationName, SpanKind kind) {
        return tracer.spanBuilder(operationName)
                .setSpanKind(kind)
                .startSpan();
    }

    public static Span startSpan(String operationName) {
        return startSpan(operationName, SpanKind.INTERNAL);
    }

    public static void addAttributes(Span span, Map<String, String> attributes) {
        if (span != null && attributes != null) {
            attributes.forEach(span::setAttribute);
        }
    }

    public static void addEvent(Span span, String eventName) {
        if (span != null) {
            span.addEvent(eventName);
        }
    }

    public static void recordException(Span span, Throwable throwable) {
        if (span != null && throwable != null) {
            span.recordException(throwable);
        }
    }

    public static Scope makeCurrent(Span span) {
        return span.makeCurrent();
    }

    public static void endSpan(Span span) {
        if (span != null) {
            span.end();
        }
    }

    public static String getTraceId() {
        Span currentSpan = Span.current();
        return currentSpan.getSpanContext().getTraceId();
    }

    public static String getSpanId() {
        Span currentSpan = Span.current();
        return currentSpan.getSpanContext().getSpanId();
    }

    public static Context getCurrentContext() {
        return Context.current();
    }
}
