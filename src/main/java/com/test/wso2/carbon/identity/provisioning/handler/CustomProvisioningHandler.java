package com.test.wso2.carbon.identity.provisioning.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.handler.provisioning.impl.DefaultProvisioningHandler;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.List;
import java.util.Map;


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

        String resolvedTenantDomain = MultitenantUtils.getTenantDomain(subject);
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

        String resolvedTenantDomain = MultitenantUtils.getTenantDomain(subject);
        super.handle(roles, subject, attributes, provisioningUserStoreId, resolvedTenantDomain, stepAttrList);
    }


}
