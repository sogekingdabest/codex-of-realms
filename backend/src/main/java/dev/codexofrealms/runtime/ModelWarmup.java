package dev.codexofrealms.runtime;

import java.util.Objects;

public record ModelWarmup(ModelWarmupState state) {
    public ModelWarmup {
        Objects.requireNonNull(state, "Warm-up state is required.");
    }
}
