# Service Level Agreement (SLA)

**DIPME, CORP** (a Delaware C Corporation)

**Effective Date:** [DATE]

---

## 1. Introduction

This Service Level Agreement ("SLA") describes the service commitments DIPME, CORP ("Dipme") provides to its customers ("Customer") for the Linqra platform.

This SLA is part of and subject to the terms of the Master Service Agreement or Terms of Service between Dipme and Customer.

---

## 2. Service Availability Commitment

### 2.1 Uptime Guarantee

Dipme commits to a **99.9% Monthly Uptime Percentage** for the Linqra platform.

| Metric | Commitment |
| :--- | :--- |
| Monthly Uptime | 99.9% |
| Allowed Downtime | ~43 minutes per month |
| Annual Downtime | ~8.76 hours per year |

### 2.2 Uptime Calculation

Monthly Uptime Percentage is calculated as:

```
((Total Minutes in Month - Downtime Minutes) / Total Minutes in Month) × 100
```

### 2.3 Excluded Downtime

The following are not counted as Downtime:

a) **Scheduled Maintenance:** Announced at least 48 hours in advance via email
b) **Emergency Maintenance:** Security patches or critical fixes that cannot wait
c) **Force Majeure:** Events beyond reasonable control (natural disasters, war, etc.)
d) **Customer-Caused Issues:** Misconfiguration, API misuse, or exceeding usage limits
e) **Third-Party Failures:** AWS outages, internet backbone issues

---

## 3. Performance Commitments

### 3.1 API Response Time

| Endpoint Type | Target Response Time | 95th Percentile |
| :--- | :--- | :--- |
| Authentication | < 200ms | < 500ms |
| Standard API Calls | < 500ms | < 1000ms |
| AI Assistant Queries | < 5 seconds | < 10 seconds |
| Document Processing | Varies by size | < 60 seconds per 10MB |

### 3.2 Data Durability

| Component | Durability | Notes |
| :--- | :--- | :--- |
| S3 Object Storage | 99.999999999% | 11 nines durability |
| MongoDB Database | 99.99% | Replica set with journaling |
| Audit Logs | 99.999999999% | S3 Object Lock (Compliance Mode) + CRR |

### 3.3 Recovery Objectives

| Metric | Commitment |
| :--- | :--- |
| **RPO** (Recovery Point Objective) | < 15 minutes for database, ~15 minutes for S3 |
| **RTO** (Recovery Time Objective) | < 4 hours |

---

## 4. Support Response Times

### 4.1 Support Tiers

| Severity | Definition | Initial Response | Resolution Target |
| :--- | :--- | :--- | :--- |
| **Critical (P1)** | Service completely unavailable | 1 hour | 4 hours |
| **High (P2)** | Major feature unavailable | 4 hours | 24 hours |
| **Medium (P3)** | Feature degraded but usable | 8 business hours | 72 hours |
| **Low (P4)** | General questions, minor issues | 2 business days | Best effort |

### 4.2 Support Channels

| Channel | Availability |
| :--- | :--- |
| Email (support@linqra.com) | 24/7, monitored during business hours |
| In-App Chat | Business hours (9am-6pm CST, Mon-Fri) |
| Phone (Enterprise only) | Business hours |

### 4.3 Business Hours

Monday through Friday, 9:00 AM to 6:00 PM Central Standard Time (CST), excluding US federal holidays.

---

## 5. Service Credits

### 5.1 Credit Schedule

If Linqra fails to meet the Monthly Uptime Percentage, Customer is entitled to a service credit:

| Monthly Uptime | Service Credit |
| :--- | :--- |
| 99.0% - 99.9% | 10% of monthly fee |
| 95.0% - 99.0% | 25% of monthly fee |
| 90.0% - 95.0% | 50% of monthly fee |
| Below 90.0% | 100% of monthly fee |

### 5.2 Credit Request Process

a) Customer must request credits within 30 days of the incident
b) Request must include dates/times of unavailability
c) Dipme will verify the claim and apply credits within 30 days
d) Credits are applied to future invoices (no cash refunds)

### 5.3 Maximum Credits

Total credits in any billing month shall not exceed 100% of that month's fees.

---

## 6. Security Commitments

### 6.1 Data Protection

| Control | Commitment |
| :--- | :--- |
| Encryption at Rest | AES-256-GCM with per-tenant keys |
| Encryption in Transit | TLS 1.2 or higher |
| Access Control | Role-based access control (RBAC) |
| Audit Logging | Comprehensive logging, 7-year retention, **Immutable** (WORM) |

### 6.2 Compliance

Dipme maintains the following compliance measures:

- Written security policies (`policies/` directory)
- Incident response procedures
- Breach notification within 24 hours
- Annual security reviews

### 6.3 Data Location

| Environment | Primary Region | Backup Region |
| :--- | :--- | :--- |
| Production | us-west-2 (Oregon) | us-east-1 (Virginia) |

---

## 7. Customer Responsibilities

Customer agrees to:

a) Maintain secure credentials and not share accounts
b) Report suspected security incidents promptly
c) Use the service in accordance with acceptable use policies
d) Provide accurate contact information for notifications
e) Keep integrations and API clients up to date

---

## 8. Scheduled Maintenance

### 8.1 Maintenance Windows

| Type | Notice Period | Typical Duration |
| :--- | :--- | :--- |
| Routine Maintenance | 48 hours | < 15 minutes |
| Major Upgrades | 7 days | < 2 hours |
| Emergency Patches | Best effort | < 30 minutes |

### 8.2 Notification

Maintenance notifications are sent via:
- Email to designated technical contacts
- In-app banner (when possible)
- Status page (status.linqra.com)

---

## 9. Monitoring and Reporting

### 9.1 Status Page

Real-time service status is available at: **status.linqra.com**

### 9.2 Monthly Reports

Upon request, Dipme will provide monthly reports including:
- Uptime percentage
- Incident summary
- Performance metrics

---

## 10. Amendments

This SLA may be updated by Dipme with 30 days notice. Material changes that negatively impact Customer will not apply to existing contracts until renewal.

---

## 11. Contact Information

**Technical Support:**
- Email: msen@linqra.com

**Security Incidents:**
- Email: msen@linqra.com
- Response: 24/7 monitoring

**Billing:**
- Email: msen@linqra.com

---

*Last Updated: December 2024*
