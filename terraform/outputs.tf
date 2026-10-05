output "api_url" {
  value = aws_apigatewayv2_api.app.api_endpoint
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "orders_queue_url" {
  value = aws_sqs_queue.orders.url
}

output "orders_dlq_url" {
  value = aws_sqs_queue.orders_dlq.url
}

output "events_table_name" {
  value = aws_dynamodb_table.events.name
}

output "inventory_table_name" {
  value = aws_dynamodb_table.inventory.name
}

output "tickets_table_name" {
  value = aws_dynamodb_table.tickets.name
}

output "orders_table_name" {
  value = aws_dynamodb_table.orders.name
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "alb_dns_name" {
  value = aws_lb.app.dns_name
}