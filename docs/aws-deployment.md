# AWS Deployment Guide

This guide covers deploying AccaBaccaGlory to AWS using Option A (Simple & Cost-Effective).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    AWS (us-east-1)                          │
│                                                              │
│  ┌─────────────┐         ┌─────────────────────────────┐    │
│  │ CloudFront  │────────▶│  S3 Bucket (React Website)  │    │
│  │   (CDN)     │         │  accabaccaglory-website     │    │
│  └─────────────┘         └─────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              AWS App Runner                          │    │
│  │         (Spring Boot Backend API)                    │    │
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
