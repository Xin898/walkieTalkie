# Walkie-Talkie

An MVP push-to-talk application using Kotlin/Jetpack Compose on Android and Java 21/Spring Boot 3 on the server.

## Layout

- `server`: Spring Boot WebSocket server with in-memory channel membership and an atomic single-speaker lock.
- `app`: Android Compose client with lifecycle-aware WebSocket state and hold-to-talk behavior.
- `protocol.md`: versioned control messages and binary Opus frame format.

## Run the server

```text
cd server
mvn spring-boot:run
```

The WebSocket endpoint is `wss://host/ws?userId=<id>`. Use TLS and replace the development identity handling with authenticated handshake/session data before deployment. Build the container with `mvn package` followed by `docker build -t walkie-talkie .`.

## Validate

```text
cd server
mvn test
```

The tests cover one-speaker contention, release and reuse, expiration, non-member rejection, and versioned error construction.

## Provision on Lightsail

Terraform provisions one Lightsail instance, a static IP, restricted SSH, public HTTP/HTTPS, Docker, and Caddy. Publish the server image first; Terraform does not build or upload images.

```text
cd server
mvn package -DskipTests
cd ..
docker build -t ghcr.io/your-org/walkie-talkie-server:0.1.0 .
docker push ghcr.io/your-org/walkie-talkie-server:0.1.0

cd terraform
copy terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your AWS region, Lightsail key pair, image, domain, and admin CIDR.
terraform init
terraform plan
terraform apply
```

Point the domain in `terraform.tfvars` to the Terraform `server_ip` output before or immediately after `terraform apply`; Caddy obtains the TLS certificate automatically. Set `WALKIE_WS_URL` in `app/build.gradle.kts` to the resulting `wss://` URL before building Android. Keep `terraform.tfvars` and AWS credentials out of source control.
