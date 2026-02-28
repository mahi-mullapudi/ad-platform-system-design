# ──────────────────────────────────────────────
# Azure Container Registry
# ──────────────────────────────────────────────
resource "azurerm_container_registry" "main" {
  name                          = replace("${var.project_name}${var.environment}acr", "-", "")
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  sku                           = "Basic"
  admin_enabled                 = false
  public_network_access_enabled = true # Basic SKU doesn't support private endpoints
  tags                          = local.common_tags

  # NOTE: Upgrade to Premium SKU to enable private endpoint + VNet access.
  # Basic is used here to stay within the ~$250/mo budget.
  # For Premium with private endpoint, set:
  #   sku                           = "Premium"
  #   public_network_access_enabled = false
  # and uncomment the private endpoint block below.
}

# Uncomment for Premium SKU with private endpoint:
#
# resource "azurerm_private_dns_zone" "acr" {
#   name                = "privatelink.azurecr.io"
#   resource_group_name = azurerm_resource_group.main.name
#   tags                = local.common_tags
# }
#
# resource "azurerm_private_dns_zone_virtual_network_link" "acr" {
#   name                  = "acr-vnet-link"
#   private_dns_zone_name = azurerm_private_dns_zone.acr.name
#   resource_group_name   = azurerm_resource_group.main.name
#   virtual_network_id    = azurerm_virtual_network.main.id
# }
#
# resource "azurerm_private_endpoint" "acr" {
#   name                = "${local.name_prefix}-acr-pe"
#   location            = azurerm_resource_group.main.location
#   resource_group_name = azurerm_resource_group.main.name
#   subnet_id           = azurerm_subnet.endpoints.id
#   tags                = local.common_tags
#
#   private_service_connection {
#     name                           = "acr-connection"
#     private_connection_resource_id = azurerm_container_registry.main.id
#     subresource_names              = ["registry"]
#     is_manual_connection           = false
#   }
#
#   private_dns_zone_group {
#     name                 = "acr-dns-group"
#     private_dns_zone_ids = [azurerm_private_dns_zone.acr.id]
#   }
# }
