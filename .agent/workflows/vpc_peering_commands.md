---
description: commands used to establish vpc peering between eks and redis/polaris
---

# VPC Peering Commands Log

These are the exact commands executed to connect the EKS Cluster VPC (`vpc-0070d7bb409137a42`) with the Redis/Polaris VPC (`vpc-bc985bd9`).

## 1. Create Peering Connection
Request peering from EKS VPC to Redis VPC.
```bash
aws ec2 create-vpc-peering-connection \
  --vpc-id vpc-0070d7bb409137a42 \
  --peer-vpc-id vpc-bc985bd9 \
  --region us-west-2
```
*Output Peering ID*: `pcx-00b9b68fa4783983b`

## 2. Accept Peering Connection
Accept the request on the Redis VPC side.
```bash
aws ec2 accept-vpc-peering-connection \
  --vpc-peering-connection-id pcx-00b9b68fa4783983b \
  --region us-west-2
```

## 3. Update Route Tables
Route traffic between the VPCs using their CIDR blocks.

**Route Redis VPC (`172.31.0.0/16`) -> Peering Connection in EKS VPC Route Tables:**
*(Run for each EKS subnet route table)*
```bash
# EKS Route Tables
for rt in rtb-032bf27abd0961bfb rtb-032d5533b4156d1c3 rtb-07f74892aaafc1650 rtb-007cf8149a06601a6 rtb-0b9a7ffcbe403d840; do
  aws ec2 create-route \
    --route-table-id $rt \
    --destination-cidr-block 172.31.0.0/16 \
    --vpc-peering-connection-id pcx-00b9b68fa4783983b \
    --region us-west-2
done
```

**Route EKS VPC (`192.168.0.0/16`) -> Peering Connection in Redis VPC Main Route Table:**
```bash
# Redis VPC Main Route Table (rtb-216da144)
aws ec2 create-route \
  --route-table-id rtb-216da144 \
  --destination-cidr-block 192.168.0.0/16 \
  --vpc-peering-connection-id pcx-00b9b68fa4783983b \
  --region us-west-2
```

## 4. Update Security Groups
Allow ingress traffic from EKS VPC CIDR (`192.168.0.0/16`) to Redis/Polaris Security Group (`sg-306de854`).

```bash
# Allow Redis (6379)
aws ec2 authorize-security-group-ingress \
  --group-id sg-306de854 \
  --protocol tcp \
  --port 6379 \
  --cidr 192.168.0.0/16 \
  --region us-west-2

# Allow Keycloak HTTP (8080)
aws ec2 authorize-security-group-ingress \
  --group-id sg-306de854 \
  --protocol tcp \
  --port 8080 \
  --cidr 192.168.0.0/16 \
  --region us-west-2

# Allow Keycloak HTTPS (8443)
aws ec2 authorize-security-group-ingress \
  --group-id sg-306de854 \
  --protocol tcp \
  --port 8443 \
  --cidr 192.168.0.0/16 \
  --region us-west-2

# Allow MinIO (9000) - Added 2026-01-23
aws ec2 authorize-security-group-ingress \
  --group-id sg-306de854 \
  --protocol tcp \
  --port 9000 \
  --cidr 192.168.0.0/16 \
  --region us-west-2
```


## 6. System Verification Commands

Use these commands to verify the state of the VPC peering infrastructure.

### Verify Peering Connection
Check if the peering connection is active.
```bash
aws ec2 describe-vpc-peering-connections \
  --vpc-peering-connection-ids pcx-00b9b68fa4783983b \
  --region us-west-2
```

### Verify Route Tables (EKS Side)
Check if route tables have the route to `172.31.0.0/16`.
```bash
aws ec2 describe-route-tables \
  --filters "Name=vpc-id,Values=vpc-0070d7bb409137a42" \
  --query 'RouteTables[*].{ID:RouteTableId, Routes:Routes[?DestinationCidrBlock==`172.31.0.0/16`]}' \
  --region us-west-2
```

### Verify Security Group Rules
Check if `sg-306de854` allows traffic on port 9000.
```bash
aws ec2 describe-security-groups \
  --group-ids sg-306de854 \
  --query 'SecurityGroups[*].IpPermissions[?ToPort==`9000`]' \
  --region us-west-2
```

### Verify EC2 Instance IP
Confirm the private IP of the Polaris instance.
```bash
aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=polaris-ec2" \
  --query 'Reservations[*].Instances[*].PrivateIpAddress' \
  --region us-west-2
```

## 7. MinIO Configuration (Hybrid Connectivity)

- **Internal Access (EKS -> EC2)**: `http://minio-service:9000`
  - Uses Kubernetes Service + Endpoints mapping to `172.31.4.38`
  - Traffic flows over VPC peering
- **Public Access (Uploads)**: `https://polaris.linqra.com`
  - Proxied via Caddy on EC2


