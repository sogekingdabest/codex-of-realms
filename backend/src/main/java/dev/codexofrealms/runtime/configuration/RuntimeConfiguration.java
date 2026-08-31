package dev.codexofrealms.runtime.configuration;

import dev.codexofrealms.runtime.application.capabilities.RuntimeModelConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RuntimeConfiguration {

    @Bean
    RuntimeModelConfiguration runtimeModelConfiguration(
        @Value("${spring.ai.model.chat:none}") String chatProvider,
        @Value("${spring.ai.ollama.chat.model:}") String chatModel,
        @Value("${spring.ai.model.embedding:none}") String embeddingProvider,
        @Value("${spring.ai.ollama.embedding.model:}") String embeddingModel
    ) {
        return RuntimeModelConfiguration.of(
            chatProvider,
            chatModel,
            embeddingProvider,
            embeddingModel
        );
    }
}
