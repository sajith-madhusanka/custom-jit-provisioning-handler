# Custom JIT Provisioning Handler — WSO2 APIM 4.5.0

A custom Just-In-Time (JIT) provisioning handler for **WSO2 API Manager 4.5.0** (update level 57)
that resolves the target tenant domain from the `sub` claim of the federated ID token,
enabling correct multi-tenant user provisioning when authenticating via an external IdP
(WSO2 Identity Server 7.2.0).

---

## Problem

In a multi-tenant APIM deployment where authentication is delegated to WSO2 IS via
federated OIDC, the default `DefaultProvisioningHandler` provisions users into whichever
tenant the service provider belongs to — regardless of which tenant the user actually
belongs to. This causes users from tenant `wso2.com` to be provisioned into
`carbon.super` (or whichever tenant hosts the service provider).

## Solution

WSO2 IS is configured to include the organisation/tenant name in the `sub` claim, producing
values such as `testPublisher@carbon.super` or `testuser@wso2.com`. This handler intercepts
the JIT provisioning call, extracts the tenant domain from `sub`, and passes the correct
tenant domain to `super.handle()`.

### `sub` claim parsing logic

| Email-as-username | `sub` format | Tenant domain source |
|---|---|---|
| Disabled | `username@tenantdomain` | Segment after `@` |
| Enabled | `user@email.com@tenantdomain` (2× `@`) | Segment after the **last** `@` |
| Enabled | `user@email.com` (1× `@`) | `http://wso2.org/claims/tenantDomain` attribute, then fallback to caller value |

---

## Project Structure

```
custom-provisioning-handler/
├── pom.xml
└── src/main/java/com/test/wso2/carbon/identity/provisioning/handler/
    ├── CustomProvisioningHandler.java           # Main handler
    └── internal/
        └── CustomProvisioningHandlerBundleActivator.java  # OSGi activator
```

---

## Build

Requirements: JDK 8+, Maven 3.x

```bash
mvn clean package
```

Output: `target/com.test.wso2.carbon.identity.provisioning.handler-1.0.0.jar`

### Framework dependency

The handler targets the identity framework version shipped with APIM 4.5.0 update 57:

| Artifact | Version |
|---|---|
| `org.wso2.carbon.identity.application.authentication.framework` | `5.25.724` |
| `org.wso2.carbon.identity.core` | `5.25.724` |

---

## Deployment

### 1 — Copy the JAR

```bash
cp target/com.test.wso2.carbon.identity.provisioning.handler-1.0.0.jar \
   <APIM_HOME>/repository/components/lib/
```

> Use `components/lib/` (not `dropins/`). This places the class on the flat classpath
> so the framework can instantiate it by name from `application-authentication.xml`.

### 2 — Register the handler in `deployment.toml`

```toml
[authentication.framework.extensions]
provisioning_handler = "com.test.wso2.carbon.identity.provisioning.handler.CustomProvisioningHandler"
```

### 3 — Restart APIM

---

## IS 7.2.0 — Required Configuration

### IS `deployment.toml` additions

```toml
[server]
hide_menu_items = []

[tenant_context]
enable_tenant_qualified_urls = false
enable_tenanted_sessions = false
```

### Subject identifier configuration in the WSO2_API application

In the IS Console, open the **WSO2_API** application → **User Attributes** tab → **Subject**:

- **Assign alternate subject identifier:** ✓
- **Subject attribute:** `Username`
- **Include user domain:** ☐
- **Include organization name:** ✓

This produces a `sub` claim in the format `username@organizationname`, which is what this
handler parses to determine the target APIM tenant.

---

## APIM — Required Supporting Configuration

### Identity Provider (WSO2_IDP) — carbon.super

Configure the WSO2_IDP in APIM Management Console (`carbon.super`) with:

- **JIT Provisioning:** Always provision to User Store Domain `PRIMARY`, **Provision silently**
- **Claim Configuration:** map IdP claim `groups` → local claim `http://wso2.org/claims/role`; Role Claim URI = `groups`
- **Role Configuration:** map `creator`/`publisher`/`subscriber` to their `Internal/` equivalents
- **OAuth2/OIDC Authenticator:** OpenID Connect User ID location = **User ID found in `sub` claim**; Scopes = `openid groups`

### Dummy IDP in non-carbon.super tenants

When APIM JIT-provisions a user into a tenant, it creates a local user–federation
association that references the IDP **by name**. If an IDP with that name does not exist
in the target tenant, the association step fails.

Create a minimal IDP named **`WSO2_IDP`** in every non-carbon.super tenant in APIM. It
only needs the same name — federated authenticators and JIT settings are not required on
the dummy IDP.

### WSO2_API Service Provider — SaaS flag

The **WSO2_API** application registered in IS must be a **SaaS application** so that users
from all IS tenants/organisations can authenticate through it.

- Customers migrated from older IS versions: the SaaS flag is likely already set. Verify
  in the legacy Management Console under **Service Providers → WSO2_API → Edit →
  SaaS Application ✓**.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| All users provisioned into `carbon.super` | Handler JAR not loaded or `deployment.toml` not updated | Confirm JAR in `components/lib/` and `provisioning_handler` key is set |
| Association error during provisioning | Dummy IDP missing in target tenant | Create `WSO2_IDP` IDP in all non-carbon.super APIM tenants |
| `sub` claim has no tenant suffix | "Include organization name" not enabled in IS | Re-check WSO2_API subject settings in IS Console |
| Users from other tenants cannot log in | WSO2_API app is not SaaS | Enable "Allow sharing with organizations" on the IS app |
| Roles missing after provisioning | Scope `groups` missing or role mapping incomplete | Add `groups` to OIDC scopes; verify IDP Role Configuration |
