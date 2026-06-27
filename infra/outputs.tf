output "instance_id" {
  description = "EC2 instance id of the compose host."
  value       = aws_instance.this.id
}

output "public_ip" {
  description = "Public IPv4 of the host (the Elastic IP when allocate_eip is on)."
  value       = local.public_ip
}

output "public_dns" {
  description = "AWS-assigned public DNS name of the instance."
  value       = aws_instance.this.public_dns
}

output "app_url" {
  description = "HTTPS URL the stack serves once Caddy has its cert."
  value       = "https://${var.domain_prefix}.${local.public_ip}.${var.nip_io_base}"
}

output "security_group_id" {
  description = "Id of the host security group."
  value       = aws_security_group.this.id
}

output "backup_bucket" {
  description = "Name of the private, versioned S3 backup bucket."
  value       = aws_s3_bucket.backups.bucket
}
