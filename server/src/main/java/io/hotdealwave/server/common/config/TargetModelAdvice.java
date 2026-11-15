package io.hotdealwave.server.common.config;

import io.hotdealwave.server.analysis.Target;
import io.hotdealwave.server.analysis.VerifierOutput;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** The analysed repository for page headers rendered by MVC views (search, response). */
@ControllerAdvice
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class TargetModelAdvice {
    private final VerifierOutput verifierOutput;

    @ModelAttribute("target")
    Target target() { return verifierOutput.target(); }
}
