resource "aws_appautoscaling_target" "ecs" {
  max_capacity = var.max_capacity
  min_capacity = var.min_capacity

  resource_id =
  "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"

  scalable_dimension = "ecs:service:DesiredCount"

  service_namespace = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name = "${var.project_name}-cpu-scaling"

  policy_type = "TargetTrackingScaling"

  resource_id =
  aws_appautoscaling_target.ecs.resource_id

  scalable_dimension =
  aws_appautoscaling_target.ecs.scalable_dimension

  service_namespace =
  aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {

    target_value = 70

    predefined_metric_specification {
      predefined_metric_type =
      "ECSServiceAverageCPUUtilization"
    }

    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}

resource "aws_appautoscaling_policy" "memory" {
  name = "${var.project_name}-memory-scaling"

  policy_type = "TargetTrackingScaling"

  resource_id =
  aws_appautoscaling_target.ecs.resource_id

  scalable_dimension =
  aws_appautoscaling_target.ecs.scalable_dimension

  service_namespace =
  aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {

    target_value = 70

    predefined_metric_specification {
      predefined_metric_type =
      "ECSServiceAverageMemoryUtilization"
    }

    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}