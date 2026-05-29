package ai.knowhub.web;

/**
 * Central API version and route prefixes.
 */
public final class ApiVersion {

    public static final String V1_PREFIX = "/api/v1";
    public static final String V1_CHAT = V1_PREFIX + "/chat";
    public static final String V1_ADMIN_AUTH = V1_PREFIX + "/admin/auth";
    public static final String V1_MANAGE = V1_PREFIX + "/manage";
    public static final String V1_MANAGE_DOCUMENT = V1_MANAGE + "/document";
    public static final String V1_MANAGE_KNOWLEDGE = V1_MANAGE + "/knowledge";

    private ApiVersion() {
    }
}
