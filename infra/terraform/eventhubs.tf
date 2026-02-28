# ──────────────────────────────────────────────
# Event Hubs Namespace (Kafka-protocol compatible)
# ──────────────────────────────────────────────
resource "azurerm_eventhub_namespace" "main" {
  name                          = "${local.name_prefix}-ehns"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  sku                           = var.eventhub_sku
  capacity                      = var.eventhub_capacity
  public_network_access_enabled = false
  tags                          = local.common_tags
}

# Event Hub: ad-click-events (source topic)
resource "azurerm_eventhub" "click_events" {
  name                = "ad-click-events"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  partition_count     = 4
  message_retention   = 1
}

# Event Hub: ad-click-aggregations (sink topic)
resource "azurerm_eventhub" "aggregations" {
  name                = "ad-click-aggregations"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  partition_count     = 4
  message_retention   = 1
}

# Consumer group for Flink processor
resource "azurerm_eventhub_consumer_group" "processor" {
  name                = "ad-click-processor"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.click_events.name
  resource_group_name = azurerm_resource_group.main.name
}

# ──────────────────────────────────────────────
# Event Hubs Private Endpoint
# ──────────────────────────────────────────────
resource "azurerm_private_dns_zone" "eventhubs" {
  name                = "privatelink.servicebus.windows.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "eventhubs" {
  name                  = "eventhubs-vnet-link"
  private_dns_zone_name = azurerm_private_dns_zone.eventhubs.name
  resource_group_name   = azurerm_resource_group.main.name
  virtual_network_id    = azurerm_virtual_network.main.id
}

resource "azurerm_private_endpoint" "eventhubs" {
  name                = "${local.name_prefix}-ehns-pe"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.endpoints.id
  tags                = local.common_tags

  private_service_connection {
    name                           = "eventhubs-connection"
    private_connection_resource_id = azurerm_eventhub_namespace.main.id
    subresource_names              = ["namespace"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "eventhubs-dns-group"
    private_dns_zone_ids = [azurerm_private_dns_zone.eventhubs.id]
  }
}

# Shared Access Policy for services (connection string stored in Key Vault)
resource "azurerm_eventhub_namespace_authorization_rule" "app" {
  name                = "app-access"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name
  listen              = true
  send                = true
  manage              = false
}
