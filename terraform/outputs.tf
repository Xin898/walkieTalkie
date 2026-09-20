output "server_ip" {
  description = "Static public IP for the Lightsail server."
  value       = aws_lightsail_static_ip.server.ip_address
}

output "websocket_url" {
  description = "WebSocket URL for the Android client."
  value       = var.domain == "" ? "ws://${aws_lightsail_static_ip.server.ip_address}/ws" : "wss://${var.domain}/ws"
}

output "instance_name" {
  value = aws_lightsail_instance.server.name
}
