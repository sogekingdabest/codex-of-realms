package dev.codexofrealms.runtime.web;

import dev.codexofrealms.runtime.ModelWarmup;
import dev.codexofrealms.runtime.RuntimeCapabilities;
import dev.codexofrealms.runtime.application.capabilities.ModelWarmupService;
import dev.codexofrealms.runtime.application.capabilities.RuntimeCapabilitiesService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
class RuntimeCapabilitiesController {

    private final RuntimeCapabilitiesService capabilitiesService;
    private final ModelWarmupService warmupService;

    RuntimeCapabilitiesController(RuntimeCapabilitiesService capabilitiesService, ModelWarmupService warmupService) {
        this.capabilitiesService = capabilitiesService;
        this.warmupService = warmupService;
    }

    @GetMapping
    RuntimeCapabilities capabilities() {
        return capabilitiesService.capabilities();
    }

    @PostMapping("/warm-up")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ModelWarmup warmUp() {
        return warmupService.warmUp();
    }
}
