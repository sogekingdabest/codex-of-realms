package dev.codexofrealms.content.application.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@org.springframework.scheduling.annotation.EnableScheduling
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
class IngestionConfiguration {
}
