# GateWise Infrastructure (Terraform)

Infrastructure-as-code for the GateWise production deploy. This is the *only* path
to the box — no click-ops (ROADMAP / CLAUDE.md §6c).

## What it provisions

The cheapest viable target that fits: a **single EC2 instance** running the whole
stack with `docker compose`, fronted by **Caddy** for automatic Let's Encrypt
TLS. No ECS, no RDS, no load balancer — one box keeps a portfolio demo
free/near-free and is honest about the scale it's at.

- **EC2 instance** (`t3.medium` by default) in the account's **default VPC** —
  the simplest viable network for a one-box deploy (see the comment in `main.tf`
  for why a bespoke VPC isn't worth it here).
- **Security group** mirroring the hardened live box: SSH only from
  `ssh_ingress_cidrs`, `80`/`443` open to the world (Caddy needs `:80` for the
  ACME challenge), everything else closed. Postgres/Redis/gateway stay internal
  to the compose network and are never published.
- **cloud-init user-data** (`user-data.sh.tftpl`) that installs Docker + the
  compose plugin, clones the repo for the deploy files, derives the box's
  `nip.io` hostname from its own public IP, and runs
  `deploy/docker-compose.prod.yml` behind Caddy.
- **Optional Elastic IP** (`allocate_eip`) so the URL survives a stop/start.
  Off by default to stay free.
- **Private, versioned S3 bucket** for backups (DB dumps, audit-log exports) —
  public access blocked, encrypted at rest, old versions expired after 90 days.

## Usage

```bash
cd infra
terraform init
terraform plan  -var 'ssh_ingress_cidrs=["203.0.113.4/32"]'
terraform apply -var 'ssh_ingress_cidrs=["203.0.113.4/32"]'
```

Useful variables (see `variables.tf` for the full list): `region`,
`instance_type`, `ssh_ingress_cidrs`, `key_name`, `allocate_eip`,
`backup_bucket_name`. Outputs include `public_ip`, `app_url`, `instance_id`,
and `backup_bucket`.

## Credentials (creds-gated)

`terraform apply` needs **AWS credentials** in the environment (e.g.
`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` or an SSO profile). Consistent with
the project's honest stance, applying is creds-gated — without AWS creds you can
still `init -backend=false` and `validate` the configuration, just not stand up
real infrastructure. No secrets are ever hardcoded here; everything is a
variable, and app secrets (LLM key, DB passwords) live in the box's `.env`, not
in this code.

## State backend

State is **local by default** so a fresh clone can `init`/`validate` with no AWS
account. For team use, create an S3 bucket (+ native S3 state locking), then
uncomment the `backend "s3"` block in `providers.tf` and re-run `terraform init`.

## Cost note

Runs cheap: a single `t3.medium` (drop to `t3.micro`/`t3.small` for the free
tier) plus a small `gp3` root volume and an S3 bucket that only holds backups.
The only thing that bills when idle is an Elastic IP if you opt into one — so it
stays off by default. **Stop the instance when not in use.**
