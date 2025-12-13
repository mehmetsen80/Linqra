# Incident Response Policy (IRP)

**Effective Date:** 2025-12-08  
**Owner:** Incident Response Team (IRT)  
**Version:** 1.0

---

## 1. Purpose
This policy defines the plan for detecting, investigating, containing, and recovering from security incidents. The goal is to minimize damage and restore normal operations as quickly as possible.

## 2. Definitions
*   **Event:** An observable occurrence in the system (e.g., a user logging in).
*   **Alert:** A notification that an event deviates from the baseline (e.g., 50 failed logins).
*   **Incident:** A confirmed compromise of confidentiality, integrity, or availability (e.g., unauthorized data exfiltration).

## 3. Incident Severity Levels

| Severity | Description | Response Time |
| :--- | :--- | :--- |
| **Critical** | Data Breach, Production Service Down (SLA Breach). | 15 Minutes |
| **High** | Suspicious Activity detected by Security Sentinel (e.g., Mass Exfiltration). | 60 Minutes |
| **Medium** | Non-critical bug exploitation without data loss. | 24 Hours |
| **Low** | Malicious scanning/probing without success. | 72 Hours |

## 4. The Incident Response Lifecycle (NIST based)

### Phase 1: Preparation
*   **Tools:** Security Sentinel Service, Audit Logs (MongoDB/S3), Alerting Dashboard.
*   **Training:** Developers are trained on secure coding.
*   **Drills:** Annual tabletop exercises.

### Phase 2: Detection & Analysis
*   **Automated Detection:** The `SecuritySentinelService` actively monitors the audit stream for:
    *   Mass Exfiltration (> 50 reads / min)
    *   Brute Force Attacks (> 10 failures / 5 min)
    *   Unauthorized Decryption attempts.
*   **Notification:** Alerts are sent via Email and the Admin Dashboard "Security Bell".

### Phase 3: Containment, Eradication, & Recovery
*   **Containment:** 
    *   Auto-Lock impacted User Accounts.
    *   Revoke API Keys.
    *   Isolate compromised microservices (Blue/Green switch).
*   **Eradication:** 
    *   Patch vulnerability.
    *   Rotate compromised keys.
*   **Recovery:**
    *   Restore data from immutable S3 backups (Object Locked).
    *   Verify system integrity before bringing online.

### Phase 4: Post-Incident Activity (Lessons Learned)
*   A **Post-Mortem Report** must be generated for all Critical/High incidents within 48 hours.
*   Action items must be added to the backlog to prevent recurrence.

## 5. Breach Notification
In the event of a confirmed Data Breach (PHI/PII), Linqra will notify affected tenants within **72 hours** (GDPR requirement) or **60 days** (HIPAA requirement), whichever is stricter or applicable.

---
**Approved By:**
Mehmet Sen (CTO)
