resource "aws_cloudwatch_log_group" "app" {
  name = "/ecs/${var.project_name}"

  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name = "/aws/apigateway/${var.project_name}"

  retention_in_days = 30
}