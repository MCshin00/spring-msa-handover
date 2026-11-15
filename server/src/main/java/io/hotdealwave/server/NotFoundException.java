package io.hotdealwave.server;

/** A requested page or response record is absent from the analysis output. */
class NotFoundException extends RuntimeException {
    NotFoundException(PageExceptionMessage message) { super(message.text()); }
}
