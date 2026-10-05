
# Infraestructura AWS con Terraform

La infraestructura AWS del proyecto se encuentra en:

```text
terraform/
```

Terraform provisiona los recursos necesarios para ejecutar el sistema de ticketing en AWS.

## Arquitectura provisionada

La infraestructura contempla:

* VPC
* Subnets públicas y privadas
* Internet Gateway
* NAT Gateway
* Security Groups
* Amazon ECR
* ECS Fargate
* Application Load Balancer
* API Gateway
* VPC Link
* DynamoDB
* Amazon SQS
* SQS Dead Letter Queue (DLQ)
* IAM Roles y Policies
* CloudWatch Logs
* CloudWatch Alarms
* ECS Auto Scaling

---

## 1. Requisitos

Instalar:

* AWS CLI
* Terraform
* Docker

Verificar:

```bash
aws --version
terraform --version
docker --version
```

---

## 2. Configurar AWS

Configurar las credenciales:

```bash
aws configure
```

Utilizar la región:

```text
us-east-1
```

Verificar las credenciales:

```bash
aws sts get-caller-identity
```

---

## 3. Entrar al directorio Terraform

Desde la raíz del proyecto:

```bash
cd terraform
```

---

## 4. Inicializar Terraform

```bash
terraform init
```

---

## 5. Formatear los archivos

```bash
terraform fmt
```

---

## 6. Validar la configuración

```bash
terraform validate
```

Debe mostrar:

```text
Success! The configuration is valid.
```

---

## 7. Revisar el plan

Antes de crear recursos:

```bash
terraform plan
```

Revisar los recursos que Terraform va a crear.

---

## 8. Crear la infraestructura

```bash
terraform apply
```

Confirmar con:

```text
yes
```

Terraform creará la infraestructura AWS definida en los archivos `.tf`.

---

## 9. Consultar los outputs

Después del despliegue:

```bash
terraform output
```

Para consultar un output específico:

```bash
terraform output api_url
```

Por ejemplo:

```bash
terraform output ecr_repository_url
terraform output ecs_cluster_name
terraform output ecs_service_name
terraform output orders_queue_url
terraform output orders_dlq_url
terraform output events_table_name
terraform output inventory_table_name
terraform output tickets_table_name
terraform output orders_table_name
```

---

## 10. Construir la aplicación

Desde la raíz del proyecto:

```bash
mvn clean package
```

---

## 11. Construir la imagen Docker

```bash
docker build -t ticketing-platform:latest .
```

---

## 12. Obtener el repositorio ECR

Volver al directorio Terraform:

```bash
cd terraform
```

Ejecutar:

```bash
export ECR_REPOSITORY=$(terraform output -raw ecr_repository_url)
```

Verificar:

```bash
echo $ECR_REPOSITORY
```

---

## 13. Autenticarse en ECR

```bash
aws ecr get-login-password \
  --region us-east-1 \
| docker login \
  --username AWS \
  --password-stdin $ECR_REPOSITORY
```

---

## 14. Etiquetar la imagen

```bash
docker tag \
  ticketing-platform:latest \
  $ECR_REPOSITORY:latest
```

---

## 15. Subir la imagen

```bash
docker push $ECR_REPOSITORY:latest
```

---

## 16. Actualizar ECS

Forzar un nuevo deployment:

```bash
aws ecs update-service \
  --cluster ticketing-platform-cluster \
  --service ticketing-platform \
  --force-new-deployment \
  --region us-east-1
```

Verificar el servicio:

```bash
aws ecs describe-services \
  --cluster ticketing-platform-cluster \
  --services ticketing-platform \
  --region us-east-1
```

---

## 17. Consultar logs

Los logs de la aplicación se encuentran en CloudWatch.

```bash
aws logs tail \
  /ecs/ticketing-platform \
  --follow \
  --region us-east-1
```

---

## 18. Verificar DynamoDB

```bash
aws dynamodb list-tables \
  --region us-east-1
```

Las tablas esperadas son:

```text
ticketing-platform-events
ticketing-platform-inventory
ticketing-platform-tickets
ticketing-platform-orders
```

---

## 19. Verificar SQS

```bash
aws sqs list-queues \
  --region us-east-1
```

Deben existir la cola principal de órdenes y su DLQ.

---

## 20. Destruir la infraestructura

Para eliminar los recursos administrados por Terraform:

```bash
terraform destroy
```

Confirmar:

```text
yes
```

> **Advertencia:** `terraform destroy` puede eliminar recursos y datos creados por Terraform, incluyendo las tablas DynamoDB. Utilizarlo únicamente en ambientes donde la eliminación de los recursos sea segura.

---

## Flujo completo

Para un despliegue desde cero:

```bash
aws sts get-caller-identity

cd terraform

terraform init
terraform fmt
terraform validate
terraform plan
terraform apply

export ECR_REPOSITORY=$(terraform output -raw ecr_repository_url)

cd ..

mvn clean package

docker build -t ticketing-platform:latest .

cd terraform

aws ecr get-login-password \
  --region us-east-1 \
| docker login \
  --username AWS \
  --password-stdin $ECR_REPOSITORY

docker tag \
  ticketing-platform:latest \
  $ECR_REPOSITORY:latest

docker push $ECR_REPOSITORY:latest

aws ecs update-service \
  --cluster ticketing-platform-cluster \
  --service ticketing-platform \
  --force-new-deployment \
  --region us-east-1
```