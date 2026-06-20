# Auvex Infrastructure (placeholder)

The Terraform that provisions Auvex on AWS will live here — VPC, a free-tier EC2
host running `docker compose`, RDS Postgres, S3 + CloudFront for the console,
TLS, and the CI → GHCR → VM deploy path. It is built in **Phase 5** of the
roadmap.

It is intentionally **not** a Gradle module: `terraform` is its own toolchain
and gets wired into CI when Phase 5 begins. This file only exists so the folder
is tracked in git before then.
