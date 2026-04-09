package com.test.wso2.carbon.identity.provisioning.handler;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.handler.provisioning.impl.DefaultProvisioningHandler;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import java.util.List;
import java.util.Map;

/**
 * Custom JIT provisioning handler for APIM 4.5.0 (update level 57).
 *
 * Extracts the tenant domain from the 'sub' claim of the federated token and
 * passes it to the parent DefaultProvisioningHandler. When email-as-username is
 * enabled the 'sub' value itself may be an email address, so a different parsing
 * strategy is applied to locate the tenant domain.
 *
 * Sub claim formats handled:
 *   Email-as-username DISABLED : "username@tenantdomain"
 *                                => tenant = segment after '@'
 *
 *   Email-as-username ENABLED  : "user@email.com@tenantdomain"  (two '@' chars)
 *                                => tenant = segment after the LAST '@'
 *
 *                                "user@email.com"  (one '@' char)
 *                                => tenant = value of the
 *                                  'http://wso2.org/claims/tenantDomain' attribute,
 *                                  or the tenantDomain argument as a fallback.
 *
 * Deployment:
 *   1. Build:  mvn clean package
 *   2. Copy:   target/com.test.wso2.carbon.identity.provisioning.handler-1.0.0.jar
 *              to <APIM_HOME>/repository/components/lib/
 *   3. Config: add to deployment.toml
 *              [authentication.framework.extensions]
 *              provisioning_handler = "com.test.wso2.carbon.identity.provisioning.handler.CustomProvisioningHandler"
 *   4. Restart the server.
 */
public class CustomProvisioningHandler extends DefaultProvisioningHandler {

    private static final Log log = LogFactory.getLog(CustomProvisioningHandler.class);

    /** Claim URI used to carry an explicit tenant domain when email-as-username is active. */
    private static final String TENANT_DOMAIN_CLAIM_URI = "http://wso2.org/claims/tenantDomain";

    private static volatile CustomProvisioningHandler instance;

    public static CustomProvisioningHandler getInstance() {
        if (instance == null) {
            synchronized (CustomProvisioningHandler.class) {
                if (instance == null) {
                    instance = new CustomProvisioningHandler();
                }
            }
        }
        return instance;
    }

    /**
     * Called by the authentication framework during JIT provisioning
     * (standard 5-argument overload).
     */
    @Override
    public void handle(List<String> roles, String subject, Map<String, String> attributes,
                       String provisioningUserStoreId, String tenantDomain) throws FrameworkException {

        String resolvedTenantDomain = resolveTenantDomain(subject, attributes, tenantDomain);
        super.handle(roles, subject, attributes, provisioningUserStoreId, resolvedTenantDomain);
    }

    /**
     * Called by the authentication framework during JIT provisioning
     * (6-argument overload that carries step-specific attributes).
     */
    @Override
    public void handle(List<String> roles, String subject, Map<String, String> attributes,
                       String provisioningUserStoreId, String tenantDomain,
                       List<String> stepAttrList) throws FrameworkException {

        String resolvedTenantDomain = resolveTenantDomain(subject, attributes, tenantDomain);
        super.handle(roles, subject, attributes, provisioningUserStoreId, resolvedTenantDomain, stepAttrList);
    }

    /**
     * Determines the correct tenant domain by parsing the sub claim value.
     *
     * @param subject              The subject identifier (value of the 'sub' claim).
     * @param attributes           IdP claim attribute map passed to the provisioning handler.
     * @param fallbackTenantDomain The tenant domain supplied by the caller; used when
     *                             extraction from the sub claim is not possible.
     * @return The resolved tenant domain, never null or blank (falls back to the
     *         supplied tenantDomain parameter).
     */
    private String resolveTenantDomain(String subject, Map<String, String> attributes,
                                       String fallbackTenantDomain) {

        if (StringUtils.isBlank(subject) || !subject.contains("@")) {
            if (log.isDebugEnabled()) {
                log.debug("sub claim is blank or contains no '@' separator. "
                        + "Falling back to caller-supplied tenant domain: " + fallbackTenantDomain);
            }
            return fallbackTenantDomain;
        }

        boolean emailUsernameEnabled = IdentityUtil.isEmailUsernameEnabled();

        if (log.isDebugEnabled()) {
            log.debug("Resolving tenant domain from sub claim. "
                    + "Email-as-username: " + emailUsernameEnabled
                    + ", subject: " + subject);
        }

        String extractedTenantDomain;

        if (!emailUsernameEnabled) {
            /*
             * Standard case: sub = "username@tenantdomain"
             * The segment after the '@' is the tenant domain.
             */
            extractedTenantDomain = subject.substring(subject.lastIndexOf('@') + 1);

        } else {
            /*
             * Email-as-username is enabled.
             * Distinguish between two sub formats:
             *
             *   a) "user@email.com@tenantdomain"  - two '@' characters.
             *      The LAST '@'-delimited segment is the tenant domain.
             *
             *   b) "user@email.com"               - single '@' character.
             *      The '@' belongs to the email address, not a tenant separator.
             *      Fall back to an explicit claim or the caller-supplied value.
             */
            int firstAt = subject.indexOf('@');
            int lastAt  = subject.lastIndexOf('@');

            if (firstAt != lastAt) {
                // Format (a): extract the segment after the last '@'.
                extractedTenantDomain = subject.substring(lastAt + 1);
            } else {
                // Format (b): attempt to read tenant domain from the attributes map.
                String tenantFromClaim = attributes != null
                        ? attributes.get(TENANT_DOMAIN_CLAIM_URI)
                        : null;

                if (StringUtils.isNotBlank(tenantFromClaim)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Email-as-username active and sub has single '@'. "
                                + "Using tenant domain from claim attribute '"
                                + TENANT_DOMAIN_CLAIM_URI + "': " + tenantFromClaim);
                    }
                    return tenantFromClaim;
                }

                log.warn("Email-as-username is enabled, sub claim has a single '@' character, "
                        + "and no '" + TENANT_DOMAIN_CLAIM_URI + "' attribute is present. "
                        + "Falling back to caller-supplied tenant domain: " + fallbackTenantDomain);
                return fallbackTenantDomain;
            }
        }

        if (StringUtils.isBlank(extractedTenantDomain)) {
            log.warn("Tenant domain extracted from sub claim is blank. "
                    + "Falling back to caller-supplied tenant domain: " + fallbackTenantDomain);
            return fallbackTenantDomain;
        }

        if (log.isDebugEnabled()) {
            log.debug("Resolved tenant domain from sub claim: " + extractedTenantDomain);
        }
        return extractedTenantDomain;
    }
}
