package dev.codexofrealms.qa.configuration;

import dev.codexofrealms.qa.application.answering.AnsweringProperties;
import dev.codexofrealms.qa.infrastructure.model.ChatModelProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AnsweringProperties.class, ChatModelProperties.class})
class QaConfiguration {
}
