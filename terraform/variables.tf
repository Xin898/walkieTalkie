variable "aws_region" {
  type        = string
  description = "AWS region for the Lightsail instance."
  default     = "us-east-1"
}

variable "availability_zone_suffix" {
  type        = string
  description = "Availability-zone suffix available in the selected region."
  default     = "a"
}

variable "instance_name" {
  type        = string
  description = "Lightsail instance name."
  default     = "walkie-talkie-server"
}

variable "environment" {
  type        = string
  description = "Deployment environment tag."
  default     = "production"
}

variable "blueprint_id" {
  type        = string
  description = "Lightsail OS blueprint."
  default     = "ubuntu_24_04"
}

variable "bundle_id" {
  type        = string
  description = "Lightsail instance size."
  default     = "nano_3_0"
}

variable "key_pair_name" {
  type        = string
  description = "Existing Lightsail key pair used for emergency SSH access."

  validation {
    condition     = trimspace(var.key_pair_name) != ""
    error_message = "key_pair_name must identify an existing Lightsail key pair."
  }
}

variable "app_image" {
  type        = string
  description = "Public container image containing the walkie-talkie server."

  validation {
    condition     = trimspace(var.app_image) != ""
    error_message = "app_image must be a published container image."
  }
}

variable "domain" {
  type        = string
  description = "DNS name for TLS, for example walkie.example.com. Leave empty for HTTP-only local testing."
  default     = ""
}

variable "app_port" {
  type        = number
  description = "Internal Spring Boot port."
  default     = 8080
}

variable "ssh_cidrs" {
  type        = list(string)
  description = "CIDR ranges permitted to SSH. Restrict this to a trusted admin IP range."
  default     = []

  validation {
    condition     = length(var.ssh_cidrs) > 0
    error_message = "ssh_cidrs must contain at least one trusted administrator CIDR."
  }
}
