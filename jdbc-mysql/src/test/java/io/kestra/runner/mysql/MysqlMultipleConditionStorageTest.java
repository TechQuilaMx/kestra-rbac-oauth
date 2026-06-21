package io.kestra.runner.mysql;

import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.AbstractMultipleConditionStorageTest;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.repository.mysql.MysqlRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

class MysqlMultipleConditionStorageTest extends AbstractMultipleConditionStorageTest {

    @Inject
    @Named("multipleconditions")
    MysqlRepository<MultipleConditionWindow> repository;

    protected MultipleConditionStorageInterface multipleConditionStorage() {
        return new MysqlMultipleConditionStorage(repository);
    }

    protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows) {
        multipleConditionStorage.save(multipleConditionWindows);
    }
}
