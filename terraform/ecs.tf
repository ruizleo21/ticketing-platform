resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family = var.project_name

  requires_compatibilities = [
    "FARGATE"
  ]

  network_mode = "awsvpc"

  cpu    = var.container_cpu
  memory = var.container_memory

  execution_role_arn =
  aws_iam_role.ecs_execution.arn

  task_role_arn =
  aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name = var.project_name

      image = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"

      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        # --------------------------------------------------
        # Spring
        # --------------------------------------------------
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "aws"
        },

        # --------------------------------------------------
        # AWS
        # --------------------------------------------------
        {
          name  = "AWS_REGION"
          value = var.aws_region
        },

        # --------------------------------------------------
        # DynamoDB
        # --------------------------------------------------
        {
          name  = "EVENTS_TABLE_NAME"
          value = aws_dynamodb_table.events.name
        },
        {
          name  = "INVENTORY_TABLE_NAME"
          value = aws_dynamodb_table.inventory.name
        },
        {
          name  = "TICKETS_TABLE_NAME"
          value = aws_dynamodb_table.tickets.name
        },
        {
          name  = "ORDERS_TABLE_NAME"
          value = aws_dynamodb_table.orders.name
        },

        # --------------------------------------------------
        # SQS
        # --------------------------------------------------
        {
          name  = "ORDERS_QUEUE_URL"
          value = aws_sqs_queue.orders.url
        },

        # --------------------------------------------------
        # Ticketing configuration
        # --------------------------------------------------
        {
          name  = "TICKETING_RESERVATION_EXPIRATION_POLL_MS"
          value = "30000"
        },
        {
          name  = "TICKETING_SQS_POLL_MS"
          value = "1000"
        },

        # --------------------------------------------------
        # Orders configuration
        # --------------------------------------------------
        {
          name  = "ORDERS_RESERVATION_DURATION_MINUTES"
          value = "10"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "ecs"
        }
      }

      healthCheck = {
        command = [
          "CMD-SHELL",
          "wget -q -O - http://localhost:${var.container_port}${var.health_check_path} || exit 1"
        ]

        interval = 30
        timeout  = 5
        retries  = 3
        startPeriod = 60
      }
    }
  ])
}