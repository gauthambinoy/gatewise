variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "eu-west-1"
}

variable "instance_type" {
  description = "EC2 instance type for the single-box compose host."
  type        = string
  default     = "t3.medium"
}

variable "ssh_ingress_cidrs" {
  description = "CIDRs allowed to reach SSH (port 22). Lock this to your own IP/range, never 0.0.0.0/0. Empty means no SSH is opened (use SSM Session Manager instead)."
  type        = list(string)
  default     = []
}

variable "domain_prefix" {
  description = "Sub-domain label placed in front of the host IP, e.g. 'auvex' gives auvex.<ip>.nip.io."
  type        = string
  default     = "auvex"
}

variable "nip_io_base" {
  description = "Wildcard-DNS base used to get a real hostname (and therefore HTTPS) without buying a domain. nip.io resolves <anything>.<ip>.nip.io to <ip>."
  type        = string
  default     = "nip.io"
}

variable "key_name" {
  description = "Name of an existing EC2 key pair for SSH. Leave empty to launch with no key (reach the box via SSM only)."
  type        = string
  default     = ""
}

variable "project" {
  description = "Project tag applied to every resource and used to derive default names."
  type        = string
  default     = "auvex"
}

variable "allocate_eip" {
  description = "Allocate a stable Elastic IP. Off by default to stay free; on means the nip.io URL survives reboots (an unattached EIP is the only thing that bills here)."
  type        = bool
  default     = false
}

variable "repo_url" {
  description = "Git URL the box clones at first boot to get the deploy files (deploy/docker-compose.prod.yml + deploy/Caddyfile)."
  type        = string
  default     = "https://github.com/gauthambinoy/auvex.git"
}

variable "root_volume_gb" {
  description = "Size of the root EBS volume in GiB."
  type        = number
  default     = 20
}

variable "ami_id" {
  description = "Override the AMI. Empty means resolve the latest Canonical Ubuntu 24.04 LTS AMI for the region from SSM."
  type        = string
  default     = ""
}

variable "ubuntu_ssm_parameter" {
  description = "Public SSM parameter Canonical publishes for the current Ubuntu 24.04 AMI id."
  type        = string
  default     = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

variable "backup_bucket_name" {
  description = "Name for the private S3 backup bucket. Empty derives a globally-unique name from the project + account id."
  type        = string
  default     = ""
}
