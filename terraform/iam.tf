data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type = "Service"

      identifiers = [
        "ecs-tasks.amazonaws.com"
      ]
    }

    actions = [
      "sts:AssumeRole"
    ]
  }
}

resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy =
  data.aws_iam_policy_document.ecs_task_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role = aws_iam_role.ecs_execution.name

  policy_arn =
  "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task" {
  name = "${var.project_name}-ecs-task-role"

  assume_role_policy =
  data.aws_iam_policy_document.ecs_task_assume_role.json
}

data "aws_iam_policy_document" "app" {

  statement {
    effect = "Allow"

    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Scan",
      "dynamodb:Query",
      "dynamodb:TransactWriteItems"
    ]

    resources = [
      aws_dynamodb_table.events.arn,
      aws_dynamodb_table.inventory.arn,
      aws_dynamodb_table.tickets.arn,
      aws_dynamodb_table.orders.arn,
      "${aws_dynamodb_table.orders.arn}/index/*"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes"
    ]

    resources = [
      aws_sqs_queue.orders.arn,
      aws_sqs_queue.orders_dlq.arn
    ]
  }
}

resource "aws_iam_policy" "app" {
  name = "${var.project_name}-app-policy"

  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_role_policy_attachment" "app" {
  role = aws_iam_role.ecs_task.name

  policy_arn = aws_iam_policy.app.arn
}