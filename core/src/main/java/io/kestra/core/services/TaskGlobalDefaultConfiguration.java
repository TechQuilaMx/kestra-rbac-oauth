package io.kestra.core.services;

import io.kestra.core.models.flows.PluginDefault;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import java.util.List;

// We need to keep it for the old task defaults even if it's deprecated
@ConfigurationProperties(value = "kestra.tasks")
@Getter
public class TaskGlobalDefaultConfiguration {
    List<PluginDefault> defaults;
}
