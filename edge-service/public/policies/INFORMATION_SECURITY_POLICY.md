# Information Security Policy (ISP)

**Effective Date:** 2025-12-08  
**Owner:** CISO / Security Team  
**Version:** 1.0

---

## 1. Purpose
The purpose of this policy is to establish the framework for establishing, implementing, maintaining, and continually improving the information security management system (ISMS) within Linqra. It ensures the confidentiality, integrity, and availability of customer and company data.

## 2. Scope
This policy applies to all employees, contractors, and third-party vendors who access Linqra's systems, data, or networks. It covers all cloud infrastructure (AWS), databases (MongoDB), and application services (Spring Boot Microservices, React Frontend).

## 3. Data Classification
All data managed by Linqra is classified into the following categories:

| Level | Classification | Examples | Handling Requirements |
| :--- | :--- | :--- | :--- |
| **L4** | **Restricted / PHI / Secret** | Customer Data, Encryption Keys, Passwords, Health Records (HIPAA). | Encrypted at Rest & Transit. Strict Need-to-Know access. MFA required. |
| **L3** | **Confidential** | Internal strategic documents, non-public designs. | Access limited to employees. no public sharing. |
| **L2** | **Internal** | Employee directories, internal announcements. | Available to all employees. |
| **L1** | **Public** | Marketing website, public documentation. | No restrictions. |

## 4. Encryption Standards
To protect Highly Confidential data:

*   **At Rest:**
    *   **Application Level:** All tenant data MUST be encrypted using **AES-256-GCM** with a unique key per tenant (`teamId`).
    *   **Database Level:** All storage volumes (EBS/Atlas) MUST be encrypted.
    *   **Backups:** All S3 objects MUST be encrypted and immutable (Object Lock) where applicable.
*   **In Transit:**
    *   All external traffic MUST use **TLS 1.2+** (HTTPS).
    *   Internal microservices communication SHOULD use mTLS or internal VPC routing.

## 5. Access Control
Access to systems follows the **Principle of Least Privilege**.

*   **Authentication:** All users must authenticate via **Single Sign-On (SSO/OIDC)**.
*   **MFA:** Multi-Factor Authentication is MANDATORY for all administrative access.
*   **Review:** Access rights are reviewed quarterly. Access is immediately revoked upon termination of employment.

## 6. Vulnerability Management
*   **Scanning:** Automated dependency scanning (GitHub Dependency Review) runs on every Pull Request.
*   **Patching:** Critical vulnerabilities (CVSS > 9.0) must be remediated within **7 days**. High vulnerabilities (CVSS > 7.0) within **30 days**.
*   **Penetration Testing:** Third-party penetration tests are conducted annually.

## 7. Acceptable Use
*   Employees must lock their screens when leaving their desk.
*   No sharing of passwords or API keys.
*   No storage of customer data on unencrypted personal devices.

## 8. Enforcement
Violations of this policy may result in disciplinary action, up to and including termination of employment and legal action.

---
**Approved By:**
Mehmet Sen (CTO)
