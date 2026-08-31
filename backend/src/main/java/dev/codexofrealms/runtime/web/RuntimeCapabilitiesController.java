package dev.codexofrealms.runtime.web;

import dev.codexofrealms.runtime.RuntimeCapabilities;
import dev.codexofrealms.runtime.application.capabilities.RuntimeCapabilitiesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
class RuntimeCapabilitiesController {

    private final RuntimeCapabilitiesService capabilitiesService;

    RuntimeCapabilitiesController(RuntimeCapabilitiesService capabilitiesService) {
        this.capabilitiesService = capabilitiesService;
    }

    @GetMapping
    RuntimeCapabilities capabilities() {
        return capabilitiesService.capabilities();
    }
}
