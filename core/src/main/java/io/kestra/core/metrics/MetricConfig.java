package io.kestra.core.metrics;

import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import lombok.Getter;

@ConfigurationProperties("kestra.metrics")
@Getter
public class MetricConfig {
    String prefix;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> tags;

    /**
     * {@link io.kestra.core.models.Label} keys included to metrics.
     */
    List<String> labels;
}
