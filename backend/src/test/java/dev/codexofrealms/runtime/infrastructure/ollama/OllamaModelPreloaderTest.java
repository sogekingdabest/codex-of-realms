package dev.codexofrealms.runtime.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;

class OllamaModelPreloaderTest {

    @Test
    void loadsTheChatModelWithTheAnswerContextWithoutGeneratingText() {
        OllamaApi api = mock(OllamaApi.class);
        ArgumentCaptor<OllamaApi.ChatRequest> request = ArgumentCaptor.forClass(OllamaApi.ChatRequest.class);

        new OllamaModelPreloader(provider(api)).loadChat("qwen3.5:4b", "5m", 8192);

        verify(api).chat(request.capture());
        assertThat(request.getValue().model()).isEqualTo("qwen3.5:4b");
        assertThat(request.getValue().messages()).isEmpty();
        assertThat(request.getValue().keepAlive()).isEqualTo("5m");
        // A different context size would make Ollama reload the model for the next answer.
        assertThat(request.getValue().options()).containsEntry("num_ctx", 8192);
        assertThat(request.getValue().stream()).isFalse();
    }

    @Test
    void loadsTheEmbeddingModelWithAShortInput() {
        OllamaApi api = mock(OllamaApi.class);
        ArgumentCaptor<OllamaApi.EmbeddingsRequest> request = ArgumentCaptor.forClass(OllamaApi.EmbeddingsRequest.class);

        new OllamaModelPreloader(provider(api)).loadEmbedding("bge-m3");

        verify(api).embed(request.capture());
        assertThat(request.getValue().model()).isEqualTo("bge-m3");
        assertThat(request.getValue().input()).hasSize(1);
    }

    @Test
    void reportsAMissingClientAndPropagatesClientFailures() {
        assertThatThrownBy(() -> new OllamaModelPreloader(provider(null)).loadEmbedding("bge-m3"))
            .isInstanceOf(IllegalStateException.class);

        OllamaApi api = mock(OllamaApi.class);
        when(api.chat(any())).thenThrow(new IllegalStateException("connection refused"));
        assertThatThrownBy(() -> new OllamaModelPreloader(provider(api)).loadChat("qwen3.5:4b", "5m", 8192))
            .hasMessage("connection refused");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OllamaApi> provider(OllamaApi api) {
        ObjectProvider<OllamaApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(api);
        return provider;
    }
}
