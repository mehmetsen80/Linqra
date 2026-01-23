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

## 5. MinIO Configuration (EKS to EC2)

**EC2 Instance Private IP:** `172.31.4.38` (Polaris)

### Update GitHub Secret for Deployment
Update `SECRETS_JSON` GitHub secret to use EC2 private IP instead of Docker service name:

```json
{
  "storage.endpoint": "http://172.31.4.38:9000",
  "storage.public.endpoint": "https://polaris.linqra.com"
}
```

### Deployment
- **EKS**: Trigger `ci.yml` workflow - automatically regenerates vault and deploys
- **EC2**: Trigger `deploy-polaris-minio.yml` - deploys MinIO service

> **Note**: VPC peering allows EKS pods to reach EC2 services via private IP. 
> The S3 client uses the private endpoint for operations, while presigned URLs use the public endpoint.

