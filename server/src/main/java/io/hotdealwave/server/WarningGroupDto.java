package io.hotdealwave.server;

import java.util.List;

/** Warnings of one reason code, with what the reader does about them. */
public record WarningGroupDto(String code, String title, String action, List<WarningDto> warnings) {}
