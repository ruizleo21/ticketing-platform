resource "aws_lb" "app" {
  name = substr(
    "${var.project_name}-alb",
    0,
    32
  )

  internal = true

  load_balancer_type = "application"

  security_groups = [
    aws_security_group.alb.id
  ]

  subnets = aws_subnet.private[*].id
}

resource "aws_lb_target_group" "app" {
  name = substr(
    "${var.project_name}-tg",
    0,
    32
  )

  port = var.container_port

  protocol = "HTTP"

  target_type = "ip"

  vpc_id = aws_vpc.main.id

  health_check {
    enabled = true

    path = var.health_check_path

    protocol = "HTTP"

    port = "traffic-port"

    healthy_threshold   = 2
    unhealthy_threshold = 3

    timeout  = 5
    interval = 30

    matcher = "200"
  }
}

resource "aws_lb_listener" "app" {
  load_balancer_arn = aws_lb.app.arn

  port = 80

  protocol = "HTTP"

  default_action {
    type = "forward"

    target_group_arn =
    aws_lb_target_group.app.arn
  }
}