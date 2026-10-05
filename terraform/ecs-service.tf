resource "aws_ecs_service" "app" {
  name = var.project_name

  cluster = aws_ecs_cluster.main.id

  task_definition =
  aws_ecs_task_definition.app.arn

  desired_count = var.desired_count

  launch_type = "FARGATE"

  platform_version = "LATEST"

  network_configuration {
    subnets = aws_subnet.private[*].id

    security_groups = [
      aws_security_group.ecs.id
    ]

    assign_public_ip = false
  }

  load_balancer {
    target_group_arn =
    aws_lb_target_group.app.arn

    container_name =
    var.project_name

    container_port =
    var.container_port
  }

  deployment_minimum_healthy_percent = 50

  deployment_maximum_percent = 200

  health_check_grace_period_seconds = 60

  depends_on = [
    aws_lb_listener.app,
    aws_iam_role_policy_attachment.app
  ]
}