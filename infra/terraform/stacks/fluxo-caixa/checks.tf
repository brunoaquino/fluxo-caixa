check "prod_forma" {
  assert {
    condition = var.environment != "prod" || (
      local.cfg.az_count == 3 &&
      local.cfg.nat_gateway_per_az &&
      local.cfg.interface_endpoint_az_count == 3 &&
      local.cfg.rds_multi_az &&
      local.cfg.rds_deletion_protection
    )
    error_message = "prod exige 3 AZs, NAT por AZ, interface endpoints nas 3 AZs, RDS Multi-AZ e deletion protection."
  }
}
