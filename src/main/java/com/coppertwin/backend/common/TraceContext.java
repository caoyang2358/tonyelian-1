package com.coppertwin.backend.common;

import org.slf4j.MDC;

public final class TraceContext {
    private TraceContext() {}
    public static String id() { return MDC.get("traceId"); }
}

