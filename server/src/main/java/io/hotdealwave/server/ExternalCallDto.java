package io.hotdealwave.server;

/** An HTTP call outside the repository, with the client address or, without a client, the target status. */
public record ExternalCallDto(String service, String id, String kind, String address, String path) {}
