package io.kestra.core.storages;

import org.slf4j.Logger;

import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NamespaceFactory {
    @Inject
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepositoryInterface;

    public Namespace of(String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(tenantId, namespace, storageInterface, namespaceFileMetadataRepositoryInterface);
    }

    public Namespace of(Logger logger, String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(logger, tenantId, namespace, storageInterface, namespaceFileMetadataRepositoryInterface);
    }
}
