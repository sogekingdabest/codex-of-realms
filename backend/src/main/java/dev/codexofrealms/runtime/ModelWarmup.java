package dev.codexofrealms.runtime;

import java.util.Objects;

public record ModelWarmup(ModelWarmupState state) {
    public ModelWarmup {
        state = Objects.requireNonNull(state, "Warm-up state is required.");
    }
}
