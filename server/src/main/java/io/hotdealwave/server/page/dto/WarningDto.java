package io.hotdealwave.server.page.dto;

/** A verifier warning: a connection the repository alone cannot make, with the page to look at. */
public record WarningDto(String kind, String reason, String evidence, String page, String target) {
    /** The status part of the reason, such as "구독자 확인 안 됨". */
    public String title() {
        int separator = reason.indexOf(": ");
        return separator < 0 ? reason : reason.substring(0, separator);
    }
    public String detail() {
        int separator = reason.indexOf(": ");
        return separator < 0 ? "" : reason.substring(separator + 2);
    }
}
