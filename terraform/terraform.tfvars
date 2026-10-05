aws_region = "us-east-1"

project_name = "ticketing-platform"

environment = "dev"

vpc_cidr = "10.20.0.0/16"

container_port = 8080

container_cpu = 512

container_memory = 1024

desired_count = 2

min_capacity = 2

max_capacity = 4

image_tag = "latest"

health_check_path = "/actuator/health"