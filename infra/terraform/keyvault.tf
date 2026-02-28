# ──────────────────────────────────────────────
# Azure Key Vault
# ──────────────────────────────────────────────
resource "azurerm_key_vault" "main" {
  name                          = "${local.name_prefix}-kv"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  soft_delete_retention_days    = 7
  purge_protection_enabled      = false
  public_network_access_enabled = true   # Required for Terraform to write secrets from local machine
  enable_rbac_authorization     = true
  tags                          = local.common_tags
}

# ──────────────────────────────────────────────
# Key Vault Private Endpoint
# ──────────────────────────────────────────────
resource "azurerm_private_dns_zone" "keyvault" {
  name                = "privatelink.vaultcore.azure.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "keyvault" {
  name                  = "keyvault-vnet-link"
  private_dns_zone_name = azurerm_private_dns_zone.keyvault.name
  resource_group_name   = azurerm_resource_group.main.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

resource "azurerm_private_endpoint" "keyvault" {
  name                = "${local.name_prefix}-kv-pe"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.endpoints.id
  tags                = local.common_tags

  private_service_connection {
    name                           = "keyvault-connection"
    private_connection_resource_id = azurerm_key_vault.main.id
    subresource_names              = ["vault"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "keyvault-dns-group"
    private_dns_zone_ids = [azurerm_private_dns_zone.keyvault.id]
  }
}

# ──────────────────────────────────────────────
# RBAC: Deployer can manage secrets
# ──────────────────────────────────────────────
resource "azurerm_role_assignment" "deployer_kv_admin" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ──────────────────────────────────────────────
# Secrets
# ──────────────────────────────────────────────
resource "azurerm_key_vault_secret" "postgres_host" {
  name         = "postgres-host"
  value        = azurerm_postgresql_flexible_server.main.fqdn
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "postgres_username" {
  name         = "postgres-username"
  value        = var.postgres_admin_username
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "postgres_password" {
  name         = "postgres-password"
  value        = var.postgres_admin_password
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "postgres_jdbc_url" {
  name         = "postgres-jdbc-url"
  value        = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.postgres_database_name}?sslmode=require"
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "postgres_r2dbc_url" {
  name         = "postgres-r2dbc-url"
  value        = "r2dbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.postgres_database_name}?sslMode=require"
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "eventhubs_connection_string" {
  name         = "eventhubs-connection-string"
  value        = azurerm_eventhub_namespace_authorization_rule.app.primary_connection_string
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}

resource "azurerm_key_vault_secret" "eventhubs_bootstrap_servers" {
  name         = "eventhubs-bootstrap-servers"
  value        = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_kv_admin]
}
