package io.kestra.webserver.models.auth;

/**
 * Permissions for fine-grained access control
 */
public enum Permission {
    // Flow permissions
    FLOWS_VIEW("flows.view"),
    FLOWS_CREATE("flows.create"),
    FLOWS_EDIT("flows.edit"),
    FLOWS_DELETE("flows.delete"),
    
    // Execution permissions
    EXECUTIONS_VIEW("executions.view"),
    EXECUTIONS_CREATE("executions.create"),
    EXECUTIONS_RESTART("executions.restart"),
    EXECUTIONS_KILL("executions.kill"),
    
    // Template permissions
    TEMPLATES_VIEW("templates.view"),
    TEMPLATES_CREATE("templates.create"),
    TEMPLATES_EDIT("templates.edit"),
    TEMPLATES_DELETE("templates.delete"),
    
    // Namespace permissions
    NAMESPACES_VIEW("namespaces.view"),
    NAMESPACES_CREATE("namespaces.create"),
    NAMESPACES_EDIT("namespaces.edit"),
    NAMESPACES_DELETE("namespaces.delete"),
    
    // KV Store permissions
    KV_VIEW("kv.view"),
    KV_CREATE("kv.create"),
    KV_EDIT("kv.edit"),
    KV_DELETE("kv.delete"),
    
    // Secrets permissions
    SECRETS_VIEW("secrets.view"),
    SECRETS_CREATE("secrets.create"),
    SECRETS_EDIT("secrets.edit"),
    SECRETS_DELETE("secrets.delete"),
    
    // Admin permissions
    ADMIN_ACCESS("admin.access"),
    ADMIN_STATS("admin.stats"),
    ADMIN_TRIGGERS("admin.triggers"),
    
    // Settings permissions
    SETTINGS_VIEW("settings.view"),
    SETTINGS_EDIT("settings.edit");
    
    private final String value;
    
    Permission(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse permission from string value
     */
    public static Permission fromString(String value) {
        if (value == null) {
            return null;
        }
        
        for (Permission permission : values()) {
            if (permission.value.equals(value)) {
                return permission;
            }
        }
        return null;
    }
}
