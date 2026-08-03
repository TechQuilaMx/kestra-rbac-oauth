package io.kestra.repository.postgres;

import org.jooq.Condition;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.jdbc.repository.AbstractJdbcKvMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresKvMetadataRepository extends AbstractJdbcKvMetadataRepository {
    @Inject
    public PostgresKvMetadataRepository(
        @Named("kvMetadata") PostgresRepository<PersistedKvMetadata> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresKvMetadataRepositoryService.findCondition(jdbcRepository, query);
    }
}
