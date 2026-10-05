resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  alarm_name =
  "${var.project_name}-ecs-high-cpu"

  comparison_operator = "GreaterThanThreshold"

  evaluation_periods = 2

  metric_name = "CPUUtilization"

  namespace = "AWS/ECS"

  period = 60

  statistic = "Average"

  threshold = 85

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }

  alarm_description =
  "ECS CPU utilization is above 85%"
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  alarm_name =
  "${var.project_name}-ecs-high-memory"

  comparison_operator = "GreaterThanThreshold"

  evaluation_periods = 2

  metric_name = "MemoryUtilization"

  namespace = "AWS/ECS"

  period = 60

  statistic = "Average"

  threshold = 85

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }

  alarm_description =
  "ECS memory utilization is above 85%"
}

resource "aws_cloudwatch_metric_alarm" "orders_dlq" {
  alarm_name =
  "${var.project_name}-orders-dlq"

  comparison_operator = "GreaterThanThreshold"

  evaluation_periods = 1

  metric_name = "ApproximateNumberOfMessagesVisible"

  namespace = "AWS/SQS"

  period = 60

  statistic = "Maximum"

  threshold = 0

  dimensions = {
    QueueName =
    aws_sqs_queue.orders_dlq.name
  }

  alarm_description =
  "Messages detected in orders DLQ"
}