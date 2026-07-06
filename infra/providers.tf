terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State is kept locally by default so a fresh clone can `terraform init` and
  # `validate` with no AWS account wired up. For shared/team use, create the
  # bucket + lock table first, then uncomment this block and re-run init to
  # migrate state to S3.
  #
  # backend "s3" {
  #   bucket       = "gatewise-tfstate"
  #   key          = "gatewise/terraform.tfstate"
  #   region       = "eu-west-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  # Tag everything consistently so the box and its bucket are easy to find and
  # to cost-attribute. Per-resource Name tags are added on top of these.
  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}
