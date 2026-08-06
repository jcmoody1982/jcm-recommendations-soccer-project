# AWS Deployment Guide

This guide covers deploying AccaBaccaGlory to AWS using a Simple & Cost-Effective approach.

## Deployment Phases

| Phase | Description | Domain |
|-------|-------------|--------|
| **Phase 1** | Initial deployment | AWS default URLs (CloudFront + App Runner) |
| **Phase 2** | Add custom domain | `accabaccaglory.com` (when ready) |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    AWS (us-east-1)                          │
│                                                              │
│  ┌─────────────┐         ┌─────────────────────────────┐    │
│  │ CloudFront  │────────▶│  S3 Bucket (React Website)  │    │
│  │   (CDN)     │         │  accabaccaglory-website     │    │
│  │  + SSL/TLS  │         └─────────────────────────────┘    │
│  └─────────────┘                                            │
│        │                                                     │
│        │ (Phase 2: Add Route 53 + ACM Certificate)          │
│        ▼                                                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              AWS App Runner                          │    │
│  │         (Spring Boot Backend API)                    │    │
│  │              + Auto SSL/TLS                          │    │
│  │                                                      │    │
│  │  ┌─────────────┐    ┌─────────────────────────┐     │    │
│  │  │   ECR       │    │   Secrets Manager       │     │    │
│  │  │  (Images)   │    │  (API Keys, DB Creds)   │     │    │
│  │  └─────────────┘    └─────────────────────────┘     │    │
│  └─────────────────────────────────────────────────────┘    │
│                              │                               │
│                              ▼                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           RDS PostgreSQL (db.t3.micro)              │    │
│  │              soccer_recommendations                  │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

# Phase 1: Initial Deployment (No Custom Domain)

## Prerequisites

- AWS CLI installed and configured
- Docker installed locally
- AWS account with appropriate permissions

## Step 1: Set Up RDS PostgreSQL

### 1.1 Create Security Group

```bash
# Create security group for RDS
aws ec2 create-security-group \
  --group-name soccer-recs-db-sg \
  --description "Security group for Soccer Recommendations RDS" \
  --region us-east-1

# Note the GroupId from output, e.g., sg-xxxxxxxxx
```

### 1.2 Create RDS Instance

```bash
aws rds create-db-instance \
  --db-instance-identifier soccer-recommendations-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 15 \
  --master-username postgres \
  --master-user-password YOUR_SECURE_PASSWORD \
  --allocated-storage 20 \
  --storage-type gp2 \
  --db-name soccer_recommendations \
  --vpc-security-group-ids sg-xxxxxxxxx \
  --publicly-accessible \
  --backup-retention-period 7 \
  --region us-east-1
```

### 1.3 Wait for RDS to be Available

```bash
aws rds wait db-instance-available \
  --db-instance-identifier soccer-recommendations-db \
  --region us-east-1
```

### 1.4 Get RDS Endpoint

```bash
aws rds describe-db-instances \
  --db-instance-identifier soccer-recommendations-db \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text \
  --region us-east-1
```

## Step 2: Store Secrets in AWS Secrets Manager

```bash
aws secretsmanager create-secret \
  --name soccer-recommendations/prod \
  --description "Production secrets for Soccer Recommendations" \
  --secret-string '{
    "DATABASE_URL": "jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/soccer_recommendations",
    "DATABASE_USERNAME": "postgres",
    "DATABASE_PASSWORD": "YOUR_SECURE_PASSWORD",
    "FOOTYSTATS_API_KEY": "YOUR_FOOTYSTATS_API_KEY"
  }' \
  --region us-east-1
```

## Step 3: Create ECR Repository

```bash
# Create repository
aws ecr create-repository \
  --repository-name soccer-recommendations-api \
  --region us-east-1

# Get login command
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin YOUR_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com
```

## Step 4: Build and Push Docker Image

```bash
# From project root directory
docker build -t soccer-recommendations-api .

# Tag for ECR
docker tag soccer-recommendations-api:latest YOUR_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/soccer-recommendations-api:latest

# Push to ECR
docker push YOUR_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/soccer-recommendations-api:latest
```

## Step 5: Deploy Backend with AWS App Runner

### 5.1 Create App Runner Service (via Console)

1. Go to AWS App Runner Console
2. Click "Create service"
3. Source: Container registry → Amazon ECR
4. Select your image: `soccer-recommendations-api:latest`
5. ECR access role: Create new role
6. Service settings:
   - Service name: `soccer-recommendations-api`
   - CPU: 0.25 vCPU
   - Memory: 0.5 GB
   - Port: 8080
7. Environment variables (from Secrets Manager):
   - DATABASE_URL
   - DATABASE_USERNAME
   - DATABASE_PASSWORD
   - FOOTYSTATS_API_KEY
8. Health check path: `/actuator/health`
9. Create & deploy

### 5.2 Or Create via CLI

Create `apprunner.yaml`:

```yaml
version: 1.0
runtime: docker
port: 8080
healthCheck:
  protocol: HTTP
  path: /actuator/health
  interval: 10
  timeout: 5
  healthyThreshold: 1
  unhealthyThreshold: 5
```

## Step 6: Deploy Frontend to S3 + CloudFront

### 6.1 Create S3 Bucket

```bash
aws s3 mb s3://accabaccaglory-website --region us-east-1

# Enable static website hosting
aws s3 website s3://accabaccaglory-website \
  --index-document index.html \
  --error-document index.html
```

### 6.2 Set Bucket Policy for Public Access

```bash
aws s3api put-bucket-policy \
  --bucket accabaccaglory-website \
  --policy '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "PublicReadGetObject",
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": "arn:aws:s3:::accabaccaglory-website/*"
      }
    ]
  }'
```

### 6.3 Build and Deploy Frontend

```bash
cd site

# Set the API URL to your App Runner service URL
echo "VITE_API_BASE_URL=https://YOUR_APP_RUNNER_URL.us-east-1.awsapprunner.com/api" > .env.production

# Build
npm run build

# Deploy to S3
aws s3 sync dist/ s3://accabaccaglory-website --delete
```

### 6.4 Create CloudFront Distribution

```bash
aws cloudfront create-distribution \
  --origin-domain-name accabaccaglory-website.s3.amazonaws.com \
  --default-root-object index.html \
  --query 'Distribution.DomainName' \
  --output text
```

### 6.5 Configure CloudFront for SPA Routing

In CloudFront Console:
1. Go to Error Pages
2. Create custom error response:
   - HTTP Error Code: 403
   - Response Page Path: /index.html
   - HTTP Response Code: 200
3. Repeat for 404 error

## Step 7: Configure CORS on Backend

Add to `WebConfig.java` if not present:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(
                "https://YOUR_CLOUDFRONT_DOMAIN.cloudfront.net",
                "http://localhost:5173"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
```

## URLs After Deployment

| Service | URL |
|---------|-----|
| Frontend | `https://dxxxxxxxxx.cloudfront.net` |
| Backend API | `https://xxxxxxxxx.us-east-1.awsapprunner.com` |
| Health Check | `https://xxxxxxxxx.us-east-1.awsapprunner.com/actuator/health` |

## Estimated Monthly Cost

| Service | Estimated Cost |
|---------|----------------|
| RDS (db.t3.micro) | $15-20 |
| App Runner (0.25 vCPU) | $5-15 |
| S3 + CloudFront | $1-5 |
| Secrets Manager | $0.40 |
| **Total** | **~$25-45/month** |

## Updating the Application

### Update Backend

```bash
# Build new image
docker build -t soccer-recommendations-api .

# Tag and push
docker tag soccer-recommendations-api:latest YOUR_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/soccer-recommendations-api:latest
docker push YOUR_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/soccer-recommendations-api:latest

# App Runner will auto-deploy on new image push (if configured)
# Or manually trigger deployment in console
```

### Update Frontend

```bash
cd site
npm run build
aws s3 sync dist/ s3://accabaccaglory-website --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id YOUR_DISTRIBUTION_ID \
  --paths "/*"
```

## Troubleshooting

### Check App Runner Logs
```bash
aws logs tail /aws/apprunner/soccer-recommendations-api --follow
```

### Check RDS Connectivity
```bash
psql -h YOUR_RDS_ENDPOINT -U postgres -d soccer_recommendations
```

### Verify Health Check
```bash
curl https://YOUR_APP_RUNNER_URL/actuator/health
```

---

# Phase 2: Add Custom Domain (When Ready)

Once you've acquired your domain (e.g., `accabaccaglory.com`), follow these steps.

## Step 1: Register Domain in Route 53 (or Transfer DNS)

### Option A: Domain purchased through Route 53
Your hosted zone is created automatically.

### Option B: Domain purchased elsewhere (GoDaddy, Namecheap, etc.)
```bash
# Create hosted zone in Route 53
aws route53 create-hosted-zone \
  --name accabaccaglory.com \
  --caller-reference "$(date +%s)" \
  --region us-east-1

# Note the nameservers from the output
# Update your domain registrar to use these nameservers
```

## Step 2: Request SSL Certificate (ACM)

```bash
# Request certificate for your domain (must be in us-east-1 for CloudFront)
aws acm request-certificate \
  --domain-name accabaccaglory.com \
  --subject-alternative-names "*.accabaccaglory.com" \
  --validation-method DNS \
  --region us-east-1

# Note the CertificateArn from the output
```

### Validate the certificate:
1. Go to **AWS Console → Certificate Manager**
2. Click on your certificate
3. Click **"Create records in Route 53"** (if using Route 53)
4. Wait for status to change to **"Issued"** (~5-30 minutes)

## Step 3: Update CloudFront with Custom Domain

1. Go to **CloudFront Console** → Your distribution
2. Click **"Edit"**
3. **Alternate domain names (CNAMEs):** Add `accabaccaglory.com` and `www.accabaccaglory.com`
4. **Custom SSL certificate:** Select your ACM certificate
5. Save changes

## Step 4: Create Route 53 DNS Records

```bash
# Get your CloudFront distribution domain name
CF_DOMAIN="dxxxxxxxxxx.cloudfront.net"

# Get your hosted zone ID
ZONE_ID=$(aws route53 list-hosted-zones-by-name \
  --dns-name accabaccaglory.com \
  --query 'HostedZones[0].Id' \
  --output text | sed 's|/hostedzone/||')

# Create A record (apex domain)
aws route53 change-resource-record-sets \
  --hosted-zone-id $ZONE_ID \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "accabaccaglory.com",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z2FDTNDATAQYW2",
          "DNSName": "'$CF_DOMAIN'",
          "EvaluateTargetHealth": false
        }
      }
    }]
  }'

# Create CNAME for www
aws route53 change-resource-record-sets \
  --hosted-zone-id $ZONE_ID \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "www.accabaccaglory.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{"Value": "accabaccaglory.com"}]
      }
    }]
  }'
```

## Step 5: (Optional) Custom Domain for API

If you want `api.accabaccaglory.com` instead of the App Runner URL:

1. Go to **App Runner Console** → Your service → **Custom domains**
2. Click **"Add domain"**
3. Enter `api.accabaccaglory.com`
4. App Runner will provide DNS records to add to Route 53
5. Add the CNAME records to Route 53
6. Wait for validation

## Step 6: Update CORS Configuration

Update App Runner environment variable:

```
CORS_ALLOWED_ORIGINS=https://accabaccaglory.com,https://www.accabaccaglory.com
```

## Step 7: Update Frontend API URL

```bash
cd site

# Update production environment
echo "VITE_API_BASE_URL=https://api.accabaccaglory.com/api" > .env.production

# Or if not using custom API domain:
# echo "VITE_API_BASE_URL=https://YOUR_APP_RUNNER_URL.us-east-1.awsapprunner.com/api" > .env.production

# Rebuild and deploy
npm run build
aws s3 sync dist/ s3://accabaccaglory-website --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id YOUR_DISTRIBUTION_ID \
  --paths "/*"
```

---

## Phase 2 Estimated Additional Costs

| Service | Cost |
|---------|------|
| Route 53 Hosted Zone | $0.50/month |
| Route 53 Queries | ~$0.40/million queries |
| ACM Certificate | Free |
| **Additional Total** | **~$1-2/month** |

---

## Final URLs

### Phase 1 (Default AWS URLs)
| Service | URL |
|---------|-----|
| Frontend | `https://dxxxxxxxxxx.cloudfront.net` |
| Backend API | `https://xxxxxxxx.us-east-1.awsapprunner.com/api` |

### Phase 2 (Custom Domain)
| Service | URL |
|---------|-----|
| Frontend | `https://accabaccaglory.com` |
| Frontend (www) | `https://www.accabaccaglory.com` |
| Backend API | `https://api.accabaccaglory.com/api` |
