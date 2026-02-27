package io.kestra.webserver.models.auth;

/**
 * User roles for authorization
 * These roles are extracted from OAuth2 provider tokens
 */
public enum Role {
    /**
     * Administrator with full access to all features
     */
    ADMIN("kestra-admin"),
    
    /**
     * Operator with limited access based on configured permissions
     */
    OPERATOR("kestra-operator");
    
    private final String value;
    
    Role(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse role from string value (case-insensitive)
     */
    public static Role fromString(String value) {
        if (value == null) {
            return null;
        }
        
        String normalizedValue = value.toLowerCase().trim();
        for (Role role : values()) {
            if (role.value.toLowerCase().equals(normalizedValue) || 
                role.name().toLowerCase().equals(normalizedValue)) {
                return role;
            }
        }
        return null;
    }
}
