package dev.codexofrealms.runtime.application.port;

import java.util.List;
import java.util.Optional;

public interface ModelRuntimeProbe {

    Optional<List<String>> installedModels();
}
