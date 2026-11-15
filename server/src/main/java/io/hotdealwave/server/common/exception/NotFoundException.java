package io.hotdealwave.server.common.exception;

import io.hotdealwave.server.page.PageExceptionMessage;

/** A requested page or response record is absent from the analysis output. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(PageExceptionMessage message) { super(message.getText()); }
}
