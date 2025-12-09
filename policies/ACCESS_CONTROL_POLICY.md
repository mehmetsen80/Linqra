# Access Control Policy

**Effective Date:** 2025-12-08  
**Owner:** IAM Team  
**Version:** 1.0

---

## 1. Purpose
This policy ensures that access to Linqra's information systems is controlled and restricted to authorized users. It implements the **Principle of Least Privilege**, ensuring users have only the access necessary to perform their job functions.

## 2. User Access & Roles
Access is Role-Based (RBAC) and managed centrally via **Keycloak**.

| Role | Permissions |
| :--- | :--- |
| **SYSTEM_ADMIN** | Full access to all configuration. No access to tenant data encryption keys (technically impossible due to key architecture). |
| **TEAM_ADMIN** | Can invite users, manage billing, and view all data *within their team*. |
| **MEMBER** | Can create and read content within their team. |
| **READ_ONLY** | Can view content but cannot edit or delete. |

## 3. Authentication Policy
*   **Passwords:** Must be at least 12 characters, complex, and rotated every 90 days (enforced by Keycloak).
*   **MFA:** Multi-Factor Authentication (TOTP) is required for all Admin roles.
*   **SSO:** Enterprise tenants are encouraged to use SAML/OIDC SSO to manage their own user lifecycles.

## 4. Offboarding
When an employee leaves or a contract ends:
1.  SSO/Keycloak account is disabled immediately (within 24 hours).
2.  Physical access tokens/badges are revoked.
3.  Laptop remote wipe command is issued.

## 5. Administrative Access
*   Access to production infrastructure (AWS Console, Database) is restricted to the specific DevOps personnel.
*   Production access requires a separate, high-security authentication method (e.g., hardware key or VPN + MFA).
*   Use of "Root" accounts is strictly prohibited for daily tasks.

## 6. Access Review
*   User access rights are reviewed **Quarterly** by the Security Team.
*   Any access permissions that are no longer valid are revoked immediately.

---
**Approved By:**
Mehmet Sen (CTO)
