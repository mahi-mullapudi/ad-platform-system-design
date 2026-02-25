#!/bin/bash
# Azure Deployment Script
# This script deploys the Reactive Ad Analytics Platform to Azure

set -e

# Configuration
RESOURCE_GROUP="ads-platform-rg"
LOCATION="eastus"
AKS_NAME="ads-platform-aks"
ACR_NAME="adsplatformacr"  # Must be globally unique
POSTGRES_SERVER="ads-platform-db"
EVENTHUB_NAMESPACE="ads-platform-events"

echo "🚀 Starting Azure Deployment for Reactive Ad Analytics Platform"
echo "=================================================="

# 1. Create Resource Group
echo "📦 Creating Resource Group..."
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

# 2. Create Azure Container Registry
echo "🐳 Creating Azure Container Registry..."
az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Basic

# 3. Create AKS Cluster
echo "☸️  Creating AKS Cluster (2 nodes, Standard_B4ms)..."
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_NAME \
  --node-count 2 \
  --node-vm-size Standard_B4ms \
  --enable-managed-identity \
  --attach-acr $ACR_NAME \
  --generate-ssh-keys

# 4. Create PostgreSQL Flexible Server
echo "🐘 Creating PostgreSQL Flexible Server..."
az postgres flexible-server create \
  --resource-group $RESOURCE_GROUP \
  --name $POSTGRES_SERVER \
  --location $LOCATION \
  --admin-user ads_admin \
  --admin-password "P@ssw0rd123!"  # Change this!
  --sku-name Standard_B2s \
  --tier Burstable \
  --storage-size 32

# Create database
az postgres flexible-server db create \
  --resource-group $RESOURCE_GROUP \
  --server-name $POSTGRES_SERVER \
  --database-name ads_platform

# Configure firewall to allow Azure services
az postgres flexible-server firewall-rule create \
  --resource-group $RESOURCE_GROUP \
  --name $POSTGRES_SERVER \
  --rule-name AllowAzureServices \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# 5. Create Event Hubs Namespace (Kafka replacement)
echo "📨 Creating Event Hubs Namespace..."
az eventhubs namespace create \
  --resource-group $RESOURCE_GROUP \
  --name $EVENTHUB_NAMESPACE \
  --location $LOCATION \
  --sku Standard \
  --capacity 2

# Create Event Hub for ad-click-events
az eventhubs eventhub create \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $EVENTHUB_NAMESPACE \
  --name ad-click-events \
  --partition-count 4 \
  --message-retention 1

# Get Event Hub connection string
EVENTHUB_CONNECTION=$(az eventhubs namespace authorization-rule keys list \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $EVENTHUB_NAMESPACE \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString \
  --output tsv)

# 6. Get AKS Credentials
echo "🔑 Getting AKS Credentials..."
az aks get-credentials \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_NAME \
  --overwrite-existing

# 7. Create Kubernetes Secrets
echo "🔐 Creating Kubernetes Secrets..."

# Get PostgreSQL connection details
POSTGRES_HOST="${POSTGRES_SERVER}.postgres.database.azure.com"

kubectl create namespace ads-platform --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic database-secret \
  --namespace ads-platform \
  --from-literal=host=$POSTGRES_HOST \
  --from-literal=username=ads_admin \
  --from-literal=password='P@ssw0rd123!' \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic eventhub-secret \
  --namespace ads-platform \
  --from-literal=connection-string="$EVENTHUB_CONNECTION" \
  --dry-run=client -o yaml | kubectl apply -f -

# 8. Build and Push Docker Images
echo "🐳 Building and Pushing Docker Images..."

# Login to ACR
az acr login --name $ACR_NAME

# Build and push ad-click-service
docker build -t ${ACR_NAME}.azurecr.io/ad-click-service:v1 ./ad-click-service
docker push ${ACR_NAME}.azurecr.io/ad-click-service:v1

# Build and push ad-click-processor
docker build -t ${ACR_NAME}.azurecr.io/ad-click-processor:v1 ./ad-click-processor
docker push ${ACR_NAME}.azurecr.io/ad-click-processor:v1

# Build and push analytics-gateway
docker build -t ${ACR_NAME}.azurecr.io/analytics-gateway:v1 ./analytics-gateway
docker push ${ACR_NAME}.azurecr.io/analytics-gateway:v1

# 9. Update Kubernetes Manifests with ACR Name
echo "📝 Updating Kubernetes Manifests..."
sed -i '' "s/\${ACR_NAME}/${ACR_NAME}/g" k8s/*.yaml

# 10. Deploy to Kubernetes
echo "☸️  Deploying to Kubernetes..."
kubectl apply -f k8s/

# 11. Wait for deployments
echo "⏳ Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=300s \
  deployment/ad-click-service \
  -n ads-platform

kubectl wait --for=condition=available --timeout=300s \
  deployment/analytics-gateway \
  -n ads-platform

# 12. Get Service URLs
echo ""
echo "✅ Deployment Complete!"
echo "=================================================="
echo ""
echo "📊 Resource Summary:"
echo "  Resource Group: $RESOURCE_GROUP"
echo "  AKS Cluster: $AKS_NAME"
echo "  PostgreSQL: $POSTGRES_HOST"
echo "  Event Hubs: $EVENTHUB_NAMESPACE"
echo "  Container Registry: ${ACR_NAME}.azurecr.io"
echo ""
echo "🌐 Service URLs:"
kubectl get services -n ads-platform
echo ""
echo "📝 To get the external IP of Analytics Gateway:"
echo "  kubectl get service analytics-gateway -n ads-platform"
echo ""
echo "📊 To view logs:"
echo "  kubectl logs -f deployment/ad-click-service -n ads-platform"
echo ""
echo "💰 Estimated Monthly Cost: ~$260"
echo "  - PostgreSQL: ~$40 (Burstable)"
echo "  - AKS: ~$120 (2x Standard_B4ms)"
echo "  - Event Hubs: ~$25 (Standard, 2 TUs)"
echo "  - Container Registry: ~$5 (Basic)"
echo "  - Networking: ~$20"
echo "  - Monitoring: ~$10"
echo ""
echo "🎯 Next Steps:"
echo "  1. Run Flyway migrations manually or on first boot"
echo "  2. Configure custom domain + SSL certificate"
echo "  3. Set up Azure Monitor alerts"
echo "  4. Enable autoscaling: kubectl autoscale deployment ad-click-service --cpu-percent=70 --min=2 --max=10 -n ads-platform"
echo "  5. Review and update database password in secrets"
echo ""
