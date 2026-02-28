# ──────────────────────────────────────────────
# Workload Identity for AKS pods
# Allows pods to authenticate to Azure services (Key Vault)
# without storing any credentials.
# ──────────────────────────────────────────────

# User-Assigned Managed Identity for the application pods
resource "azurerm_user_assigned_identity" "app" {
  name                = "${local.name_prefix}-app-identity"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# Federated credential linking K8s service account → Azure Managed Identity
resource "azurerm_federated_identity_credential" "app" {
  name                = "ads-platform-federated"
  resource_group_name = azurerm_resource_group.main.name
  parent_id           = azurerm_user_assigned_identity.app.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject             = "system:serviceaccount:ads-platform:ads-platform-sa"
}

# ──────────────────────────────────────────────
# RBAC: App identity can read Key Vault secrets
# ──────────────────────────────────────────────
resource "azurerm_role_assignment" "app_kv_reader" {
  scope                            = azurerm_key_vault.main.id
  role_definition_name             = "Key Vault Secrets User"
  principal_id                     = azurerm_user_assigned_identity.app.principal_id
  skip_service_principal_aad_check = true
}

# ──────────────────────────────────────────────
# RBAC: App identity can send/receive Event Hubs
# ──────────────────────────────────────────────
resource "azurerm_role_assignment" "app_eventhubs_sender" {
  scope                            = azurerm_eventhub_namespace.main.id
  role_definition_name             = "Azure Event Hubs Data Sender"
  principal_id                     = azurerm_user_assigned_identity.app.principal_id
  skip_service_principal_aad_check = true
}

resource "azurerm_role_assignment" "app_eventhubs_receiver" {
  scope                            = azurerm_eventhub_namespace.main.id
  role_definition_name             = "Azure Event Hubs Data Receiver"
  principal_id                     = azurerm_user_assigned_identity.app.principal_id
  skip_service_principal_aad_check = true
}
