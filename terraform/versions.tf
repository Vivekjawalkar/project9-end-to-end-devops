terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.62.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "Project9-End-to-End-DevOps"
      Environment = "Production"
      ManagedBy   = "Terraform"
    }
  }
}
