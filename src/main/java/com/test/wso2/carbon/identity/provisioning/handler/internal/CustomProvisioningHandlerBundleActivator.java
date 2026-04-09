package com.test.wso2.carbon.identity.provisioning.handler.internal;

import com.test.wso2.carbon.identity.provisioning.handler.CustomProvisioningHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(
        name = "com.test.wso2.carbon.identity.provisioning.handler.internal.CustomProvisioningHandlerBundleActivator",
        immediate = true
)
public class CustomProvisioningHandlerBundleActivator {

    @Activate
    protected void activate(BundleContext bundleContext) {
        bundleContext.registerService(
                CustomProvisioningHandler.class,
                CustomProvisioningHandler.getInstance(),
                null);
    }
}
