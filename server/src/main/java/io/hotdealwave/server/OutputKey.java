package io.hotdealwave.server;

/** Keys of verifier output and its confirmation legend. */
enum OutputKey {
    ENDPOINTS("endpoints"),
    HTTP_CALLS("httpCalls"),
    HTTP_CLIENTS("httpClients"),
    MESSAGE_PUBLICATIONS("messagePublications"),
    MESSAGE_SUBSCRIPTIONS("messageSubscriptions"),
    MESSAGE_LINKS("messageLinks"),
    CALLS("calls"),
    THROWS("throws"),
    SERVICES("services"),
    ENVIRONMENT_VARIABLES("environmentVariables"),
    EXTERNAL_RESOURCES("externalResources"),
    ERROR_CODES("errorCodes"),
    EXCEPTION_HANDLERS("exceptionHandlers"),
    GATEWAY_REJECTIONS("gatewayRejections"),
    PAGE_SERIALIZATION("pageSerialization"),
    UNSUPPORTED("unsupported"),
    KNOWLEDGE("knowledge"),
    WARNINGS("warnings"),
    WARNING_CODES("warningCodes"),
    CONFIRMED("confirmed"),
    TARGET("target"),
    REPOSITORY("repository"),
    CHECKS("checks"),
    FIELDS("fields"),
    EXTERNAL_PATHS("externalPaths"),
    TOKEN_REQUIRED("tokenRequired"),
    ERRORS("errors");

    private final String text;

    OutputKey(String text) { this.text = text; }

    public String text() { return text; }
}
