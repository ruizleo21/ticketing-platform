resource "aws_apigatewayv2_vpc_link" "app" {
  name = "${var.project_name}-vpc-link"

  subnet_ids = aws_subnet.private[*].id

  security_group_ids = [
    aws_security_group.vpc_link.id
  ]
}

resource "aws_apigatewayv2_api" "app" {
  name = var.project_name

  protocol_type = "HTTP"

  description = "Ticketing Platform API"
}

resource "aws_apigatewayv2_integration" "app" {
  api_id = aws_apigatewayv2_api.app.id

  integration_type = "HTTP_PROXY"

  integration_method = "ANY"

  integration_uri =
  aws_lb_listener.app.arn

  connection_type = "VPC_LINK"

  connection_id =
  aws_apigatewayv2_vpc_link.app.id

  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "default" {
  api_id = aws_apigatewayv2_api.app.id

  route_key = "$default"

  target = "integrations/${aws_apigatewayv2_integration.app.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id = aws_apigatewayv2_api.app.id

  name = "$default"

  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}