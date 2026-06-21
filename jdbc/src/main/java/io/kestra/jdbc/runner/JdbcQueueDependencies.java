package io.kestra.jdbc.runner;

import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Singleton;

@Singleton
@JdbcRunnerEnabled
public record JdbcQueueDependencies(JooqDSLContextWrapper jooqDSLContextWrapper) {
}
