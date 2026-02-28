# Azure Deployment Guide

End-to-end guide for deploying the Ads Analytics Platform to Azure using Terraform + AKS.

## Prerequisites

Install these tools:

```bash
# Azure CLI
brew install azure-cli

# Terraform
brew install terraform

# kubectl
brew install kubernetes-cli

# Helm (for NGINX Ingress)
brew install helm
```

Login to Azure:

```bash
az login
az account set --subscription "<YOUR_SUBSCRIPTION_ID>"
```

## Step 1: Provision Infrastructure with Terraform

```bash
cd infra/terraform

# Initialize Terraform
terraform init

# Set the PostgreSQL password (never put this in a file)
export TF_VAR_postgres_admin_password="$(openssl rand -base64 24)"
echo "Save this password securely: $TF_VAR_postgres_admin_password"

# Preview what will be created
terraform plan

# Apply (type 'yes' when prompted)
terraform apply
```

This creates: Resource Group, VNet with 3 subnets, AKS cluster, PostgreSQL Flexible Server, Event Hubs, Key Vault, ACR, Private Endpoints, Managed Identity, and all RBAC bindings.

Takes ~10-15 minutes.

## Step 2: Connect to AKS

```bash
# Get credentials (command is also in terraform output)
$(terraform output -raw aks_get_credentials_command)

# Verify
kubectl get nodes
```

## Step 3: Install NGINX Ingress Controller

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz
```

## Step 4: Update K8s Manifests with Terraform Outputs

```bash
cd ../..  # back to project root

# Get values from Terraform
ACR_SERVER=$(terraform -chdir=infra/terraform output -raw acr_login_server)
CLIENT_ID=$(terraform -chdir=infra/terraform output -raw app_identity_client_id)
TENANT_ID=$(terraform -chdir=infra/terraform output -raw tenant_id)
KV_NAME=$(terraform -chdir=infra/terraform output -raw keyvault_name)

# Substitute placeholders in namespace/secrets manifest
sed -i '' "s|<APP_IDENTITY_CLIENT_ID>|${CLIENT_ID}|g" k8s/00-namespace-secrets.yaml
sed -i '' "s|<TENANT_ID>|${TENANT_ID}|g" k8s/00-namespace-secrets.yaml
sed -i '' "s|<KEYVAULT_NAME>|${KV_NAME}|g" k8s/00-namespace-secrets.yaml

# Substitute ACR in deployment manifests
sed -i '' "s|<ACR_LOGIN_SERVER>|${ACR_SERVER}|g" k8s/ad-click-service.yaml
sed -i '' "s|<ACR_LOGIN_SERVER>|${ACR_SERVER}|g" k8s/ad-click-processor.yaml
sed -i '' "s|<ACR_LOGIN_SERVER>|${ACR_SERVER}|g" k8s/analytics-gateway.yaml

# Generate the Event Hubs SASL secret
CONN_STR=$(terraform -chdir=infra/terraform output -raw eventhubs_connection_string 2>/dev/null)
sed "s|__CONN_STR__|${CONN_STR}|g" k8s/eventhubs-sasl-secret.yaml.tpl > k8s/eventhubs-sasl-secret.yaml
```

## Step 5: Build & Push Docker Images

```bash
ACR_NAME=$(terraform -chdir=infra/terraform output -raw acr_name)

# Login to ACR
az acr login --name $ACR_NAME

# Build from project root (Dockerfiles reference parent pom.xml)
docker build -t ${ACR_SERVER}/ad-click-service:v1 -f ad-click-service/Dockerfile .
docker build -t ${ACR_SERVER}/ad-click-processor:v1 -f ad-click-processor/Dockerfile .
docker build -t ${ACR_SERVER}/analytics-gateway:v1 -f analytics-gateway/Dockerfile .

# Push
docker push ${ACR_SERVER}/ad-click-service:v1
docker push ${ACR_SERVER}/ad-click-processor:v1
docker push ${ACR_SERVER}/analytics-gateway:v1
```

## Step 6: Deploy to AKS

```bash
# Apply manifests in order
kubectl apply -f k8s/00-namespace-secrets.yaml
kubectl apply -f k8s/eventhubs-sasl-secret.yaml
kubectl apply -f k8s/ad-click-service.yaml
kubectl apply -f k8s/ad-click-processor.yaml
kubectl apply -f k8s/analytics-gateway.yaml
kubectl apply -f k8s/ingress.yaml

# Wait for pods
kubectl get pods -n ads-platform -w
```

## Step 7: Verify

```bash
# Get the external IP
kubectl get ingress -n ads-platform
EXTERNAL_IP=$(kubectl get ingress ads-platform-ingress -n ads-platform -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Test click event
curl -X POST http://${EXTERNAL_IP}/api/v1/events/clicks \
  -H "Content-Type: application/json" \
  -d '{"adId":"ad-1","campaignId":"camp-1","timestamp":"2026-02-09T10:00:00Z"}'

# Query analytics
curl "http://${EXTERNAL_IP}/api/v1/analytics/campaign/camp-1/summary?start=2026-02-09T00:00:00Z&end=2026-02-10T00:00:00Z"
```

## Operations

### View logs

```bash
kubectl logs -f deployment/ad-click-service -n ads-platform
kubectl logs -f deployment/ad-click-processor -n ads-platform
kubectl logs -f deployment/analytics-gateway -n ads-platform
```

### Scale

```bash
kubectl scale deployment ad-click-service --replicas=3 -n ads-platform

# Or set up HPA (autoscale at 70% CPU)
kubectl autoscale deployment ad-click-service \
  --cpu-percent=70 --min=2 --max=10 -n ads-platform
```

### Tear down everything

```bash
cd infra/terraform
terraform destroy
```

## Security Summary

| Layer | Mechanism |
|---|---|
| Network | All resources in a single VNet; PostgreSQL via VNet integration; Key Vault/Event Hubs via Private Endpoints; no public DB access |
| Authentication | AKS Workload Identity (OIDC) — pods authenticate as Azure Managed Identity, zero stored credentials |
| Secrets | Azure Key Vault + CSI driver; secrets synced to K8s Secrets automatically, rotated every 5 min |
| Container images | ACR with Managed Identity pull (no docker login creds); non-root containers |
| TLS | NGINX Ingress + cert-manager for automatic Let's Encrypt certificates (uncomment in ingress.yaml) |
| RBAC | Least-privilege: app identity can only read KV secrets + send/receive Event Hubs |
| Audit | PostgreSQL connection logging enabled; Container Insights via Log Analytics |
| CI/CD | GitHub OIDC federation — no long-lived Azure credentials in GitHub |
