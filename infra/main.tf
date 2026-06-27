data "aws_caller_identity" "current" {}

# We use the account's default VPC rather than carving a bespoke one. This is a
# single demo box that only needs a public subnet with internet access; a custom
# VPC would add subnets/route tables/IGW (and, if private, a paid NAT gateway)
# for zero benefit on a one-instance deploy. Simplest viable wins here.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Latest Canonical Ubuntu 24.04 LTS AMI for the region, straight from the SSM
# parameter Canonical maintains, so we never pin a stale image id.
data "aws_ssm_parameter" "ubuntu" {
  name = var.ubuntu_ssm_parameter
}

locals {
  ami_id        = var.ami_id != "" ? var.ami_id : data.aws_ssm_parameter.ubuntu.value
  backup_bucket = var.backup_bucket_name != "" ? var.backup_bucket_name : "${var.project}-backups-${data.aws_caller_identity.current.account_id}"
  public_ip     = var.allocate_eip ? aws_eip.this[0].public_ip : aws_instance.this.public_ip
}

# Hardened security group mirroring the live box: SSH only from known CIDRs,
# HTTP/HTTPS open to the world (Caddy needs :80 for the ACME challenge and :443
# to serve), everything else closed. Postgres/Redis/gateway stay internal to the
# compose network and are never published.
resource "aws_security_group" "this" {
  name_prefix = "${var.project}-"
  description = "Auvex single-box: SSH from allowed CIDRs, HTTP/HTTPS public, all else closed."
  vpc_id      = data.aws_vpc.default.id

  dynamic "ingress" {
    # Only emitted when ssh_ingress_cidrs is non-empty, so an empty list opens no
    # SSH at all rather than defaulting to the world.
    for_each = length(var.ssh_ingress_cidrs) > 0 ? [1] : []
    content {
      description = "SSH from operator IP/range only"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.ssh_ingress_cidrs
    }
  }

  ingress {
    description = "HTTP (Let's Encrypt ACME challenge + redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound (pull images, reach the LLM provider, ACME)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project}-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_instance" "this" {
  ami                         = local.ami_id
  instance_type               = var.instance_type
  subnet_id                   = element(tolist(data.aws_subnets.default.ids), 0)
  vpc_security_group_ids      = [aws_security_group.this.id]
  associate_public_ip_address = true
  key_name                    = var.key_name != "" ? var.key_name : null

  # cloud-init brings up Docker + the compose stack behind Caddy on first boot.
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    repo_url      = var.repo_url
    domain_prefix = var.domain_prefix
    nip_io_base   = var.nip_io_base
  })

  root_block_device {
    volume_size = var.root_volume_gb
    volume_type = "gp3"
    encrypted   = true
  }

  metadata_options {
    # Require IMDSv2; the user-data fetches the public IP via a token request.
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  tags = {
    Name = "${var.project}-host"
  }
}

# Optional stable IP. Off by default (an EIP only bills while it exists), on when
# you want the nip.io URL to survive a stop/start.
resource "aws_eip" "this" {
  count    = var.allocate_eip ? 1 : 0
  instance = aws_instance.this.id
  domain   = "vpc"

  tags = {
    Name = "${var.project}-eip"
  }
}

# Private, versioned backup bucket (DB dumps, audit-log exports). Public access
# is blocked four ways and objects are encrypted at rest.
resource "aws_s3_bucket" "backups" {
  bucket = local.backup_bucket

  tags = {
    Name = "${var.project}-backups"
  }
}

resource "aws_s3_bucket_versioning" "backups" {
  bucket = aws_s3_bucket.backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket                  = aws_s3_bucket.backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Don't keep old backup versions forever; expire superseded versions after 90
# days to keep storage cost flat.
resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    id     = "expire-noncurrent"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}
