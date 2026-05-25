prefix      = "cg"
environment = "dev"
location    = "southcentralus"

vnet_address_space          = ["10.10.0.0/16"]
aks_subnet_address_prefixes = ["10.10.1.0/24"]

kubernetes_version = "1.33"
node_count         = 1
node_vm_size       = "Standard_B2pls_v2"
os_disk_size_gb    = 32
aks_sku_tier       = "Free"

storage_replication = "LRS"

extra_tags = {
  cost_center = "student-credit"
  owner       = "ops-jose"
}
