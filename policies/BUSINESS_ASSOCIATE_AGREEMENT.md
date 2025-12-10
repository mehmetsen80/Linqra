# Business Associate Agreement (BAA)

**DIPME, CORP** (a Delaware C Corporation)

**Effective Date:** [DATE]

---

## 1. Parties

This Business Associate Agreement ("Agreement") is entered into between:

- **Covered Entity:** [CUSTOMER NAME] ("Customer")
- **Business Associate:** DIPME, CORP ("Dipme"), provider of the Linqra platform

---

## 2. Purpose

This Agreement is made pursuant to the Health Insurance Portability and Accountability Act of 1996 ("HIPAA"), the Health Information Technology for Economic and Clinical Health Act ("HITECH Act"), and regulations promulgated thereunder (collectively, the "HIPAA Rules").

The purpose of this Agreement is to ensure that Dipme will appropriately safeguard Protected Health Information ("PHI") that it creates, receives, maintains, or transmits on behalf of Customer.

---

## 3. Definitions

- **Protected Health Information (PHI):** Any information that relates to the past, present, or future physical or mental health condition of an individual; the provision of health care to an individual; or payment for health care, and that identifies or could reasonably be used to identify the individual.
- **Electronic Protected Health Information (ePHI):** PHI that is transmitted or maintained in electronic media.
- **Security Incident:** The attempted or successful unauthorized access, use, disclosure, modification, or destruction of information or interference with system operations.

---

## 4. Obligations of Dipme (Business Associate)

### 4.1 Safeguards

Dipme agrees to:

a) **Implement Administrative Safeguards:**
   - Maintain security policies and procedures (see `policies/INFORMATION_SECURITY_POLICY.md`)
   - Conduct workforce training on PHI handling
   - Designate a security officer responsible for HIPAA compliance

b) **Implement Physical Safeguards:**
   - Data hosted in AWS data centers with SOC 2 Type II certification
   - Physical access controls managed by AWS

c) **Implement Technical Safeguards:**
   - **Encryption at Rest:** AES-256-GCM encryption for all PHI stored in databases and object storage
   - **Encryption in Transit:** TLS 1.2 or higher for all data transmission
   - **Per-Tenant Encryption Keys:** Each Customer receives a unique cryptographic key; Dipme cannot access Customer data
   - **Access Controls:** Role-based access control (RBAC) integrated with SSO
   - **Audit Logging:** Comprehensive logging of all access to PHI, retained for 7 years

### 4.2 Use and Disclosure

Dipme agrees to:

a) Not use or disclose PHI other than as permitted by this Agreement or as required by law
b) Not use or disclose PHI in a manner that would violate the HIPAA Rules if done by Customer
c) Use PHI only for the purpose of providing the services described in the underlying service agreement

### 4.3 Minimum Necessary

Dipme agrees to request, use, and disclose only the minimum amount of PHI necessary to accomplish the intended purpose.

### 4.4 Subcontractors

Dipme agrees to:

a) Ensure that any subcontractors that create, receive, maintain, or transmit PHI agree to the same restrictions and conditions as contained in this Agreement
b) Maintain a list of subcontractors with access to PHI:
   - **Amazon Web Services (AWS):** Infrastructure provider with BAA in place
   - **MongoDB Atlas:** Database provider with BAA available upon request

### 4.5 Access to PHI

Upon Customer's request, Dipme agrees to provide access to PHI in a designated record set to enable Customer to fulfill its obligations under 45 CFR § 164.524.

### 4.6 Amendment of PHI

Upon Customer's request, Dipme agrees to make amendments to PHI as directed by Customer, to enable Customer to fulfill its obligations under 45 CFR § 164.526.

### 4.7 Accounting of Disclosures

Upon Customer's request, Dipme agrees to document and make available information required for Customer to provide an accounting of disclosures under 45 CFR § 164.528.

---

## 5. Security Incident and Breach Notification

### 5.1 Security Incident Notification

Dipme agrees to report to Customer any Security Incident of which it becomes aware within **24 hours** of discovery.

### 5.2 Breach Notification

In the event of a Breach of Unsecured PHI, Dipme agrees to:

a) Notify Customer within **24 hours** of discovery of the Breach
b) Provide Customer with the following information:
   - Identification of each individual whose PHI was involved
   - Date of the Breach and date of discovery
   - Description of the types of PHI involved
   - Description of what Dipme is doing to investigate, mitigate, and prevent future breaches
c) Cooperate with Customer in conducting any required notifications

### 5.3 Breach Mitigation

Dipme maintains the following breach prevention and mitigation measures:

- **SecuritySentinelService:** Real-time anomaly detection for mass exfiltration, failed logins, and unusual activity
- **Automated Alerts:** Email and UI notifications to administrators
- **Incident Response Plan:** Documented in `policies/INCIDENT_RESPONSE_POLICY.md`

---

## 6. Term and Termination

### 6.1 Term

This Agreement shall be effective as of the Effective Date and shall remain in effect until the underlying service agreement is terminated.

### 6.2 Termination

Upon termination of this Agreement, Dipme agrees to:

a) Return or destroy all PHI received from Customer within 30 days
b) If return or destruction is not feasible, extend the protections of this Agreement indefinitely
c) Provide certification of destruction upon request

### 6.3 Crypto-Shredding

Dipme supports **Crypto-Shredding** as a method of PHI destruction. Upon termination, Customer's unique encryption key can be destroyed, rendering all stored PHI mathematically unrecoverable.

---

## 7. Miscellaneous

### 7.1 Amendment

This Agreement may be amended only by written agreement signed by both parties.

### 7.2 Regulatory Changes

The parties agree to take such action as is necessary to amend this Agreement to comply with changes in HIPAA Rules.

### 7.3 Interpretation

Any ambiguity in this Agreement shall be resolved in favor of a meaning that permits compliance with the HIPAA Rules.

### 7.4 No Third-Party Beneficiaries

Nothing in this Agreement shall confer upon any person other than the parties any rights or remedies.

---

## 8. Signatures

**COVERED ENTITY (Customer):**

Signature: _________________________ Date: _____________

Name: _________________________

Title: _________________________

Company: _________________________

---

**BUSINESS ASSOCIATE (DIPME, CORP):**

Signature: _________________________ Date: _____________

Name: _________________________

Title: _________________________

---

## Appendix A: Technical Security Measures

| Control | Implementation |
| :--- | :--- |
| Encryption at Rest | AES-256-GCM, per-tenant keys |
| Encryption in Transit | TLS 1.2+ |
| Access Control | RBAC via Keycloak SSO |
| Audit Logging | Immutable logs, 7-year retention, S3 archival |
| Backup | Cross-region replication (us-west-2 → us-east-1) |
| Breach Detection | SecuritySentinelService with real-time alerts |
| Key Management | Vault system with encrypted filesystem |
| Data Destruction | Crypto-shredding supported |

---

*This template is provided for informational purposes. Consult with legal counsel before execution.*
