# ──────────────────────────────────────────────
# Default variable values for dev environment
# ──────────────────────────────────────────────

project_name = "ads-platform"
environment  = "dev"
location     = "centralus"

# AKS
aks_node_count  = 2
aks_node_vm_size = "Standard_B2s"
kubernetes_version = "1.32"

# PostgreSQL
postgres_sku            = "GP_Standard_D2s_v3"
postgres_storage_mb     = 32768
postgres_admin_username = "ads_admin"
postgres_database_name  = "ads_platform"

# Event Hubs
eventhub_sku      = "Standard"
eventhub_capacity = 1

# IMPORTANT: Set the password via environment variable, never commit it:
#   export TF_VAR_postgres_admin_password="YourSecurePassword123!"
# postgres_admin_password = ""
