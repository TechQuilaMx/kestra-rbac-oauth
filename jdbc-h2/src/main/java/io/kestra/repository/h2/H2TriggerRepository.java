package io.kestra.repository.h2;

import java.util.Date;

import org.jooq.Field;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.services.JdbcFilterService;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2TriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public H2TriggerRepository(@Named("triggers") H2Repository<Trigger> repository,
        JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}
