package dev.codexofrealms.content.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({IngestionProperties.class, StorageProperties.class})
class IngestionConfiguration {
}
