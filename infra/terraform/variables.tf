variable "project_name" {
  description = "Base name for all resources"
  type        = string
  default     = "ads-platform"
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "eastus"
}

variable "vnet_address_space" {
  description = "VNet CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "aks_subnet_cidr" {
  description = "AKS subnet CIDR"
  type        = string
  default     = "10.0.1.0/24"
}

variable "db_subnet_cidr" {
  description = "PostgreSQL delegated subnet CIDR"
  type        = string
  default     = "10.0.2.0/24"
}

variable "endpoints_subnet_cidr" {
  description = "Private endpoints subnet CIDR"
  type        = string
  default     = "10.0.3.0/24"
}

# AKS
variable "aks_node_count" {
  description = "Number of AKS worker nodes"
  type        = number
  default     = 2
}

variable "aks_node_vm_size" {
  description = "VM size for AKS nodes"
  type        = string
  default     = "Standard_B2s"
}

variable "kubernetes_version" {
  description = "Kubernetes version for AKS"
  type        = string
  default     = "1.32"
}

# PostgreSQL
variable "postgres_sku" {
  description = "PostgreSQL Flexible Server SKU"
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  description = "PostgreSQL storage in MB"
  type        = number
  default     = 32768
}

variable "postgres_admin_username" {
  description = "PostgreSQL admin username"
  type        = string
  default     = "ads_admin"
}

variable "postgres_admin_password" {
  description = "PostgreSQL admin password"
  type        = string
  sensitive   = true
}

variable "postgres_database_name" {
  description = "Application database name"
  type        = string
  default     = "ads_platform"
}

# Event Hubs
variable "eventhub_sku" {
  description = "Event Hubs SKU"
  type        = string
  default     = "Standard"
}

variable "eventhub_capacity" {
  description = "Event Hubs throughput units"
  type        = number
  default     = 1
}

# Tags
variable "tags" {
  description = "Tags applied to all resources"
  type        = map(string)
  default = {
    project   = "ads-platform"
    managed   = "terraform"
  }
}
