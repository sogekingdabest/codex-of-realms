package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

class ModelRetryConfigurationTest {

    @Test
    void callsTheModelOnceWhenItTimesOut() throws IOException {
        List<PropertySource<?>> application = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yaml"));

        new ApplicationContextRunner()
            .withInitializer(context -> application.forEach(context.getEnvironment().getPropertySources()::addFirst))
            .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
            .run(context -> {
                AtomicInteger calls = new AtomicInteger();

                assertThatThrownBy(() -> context.getBean(RetryTemplate.class).execute(() -> {
                    calls.incrementAndGet();
                    throw new ResourceAccessException("Read timed out");
                })).isInstanceOf(RetryException.class);
                assertThat(calls).hasValue(1);
            });
    }
}
