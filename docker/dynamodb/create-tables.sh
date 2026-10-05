#!/bin/bash

set -e

DYNAMODB_ENDPOINT="${DYNAMODB_ENDPOINT:-http://localhost:8000}"
AWS_REGION="${AWS_REGION:-us-east-1}"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-local}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-local}"
export AWS_DEFAULT_REGION="$AWS_REGION"

echo "=========================================="
echo " DynamoDB - Table initialization"
echo "=========================================="
echo "Endpoint: $DYNAMODB_ENDPOINT"
echo "Region:   $AWS_REGION"
echo

# --------------------------------------------------
# Wait for DynamoDB
# --------------------------------------------------

echo "Waiting for DynamoDB..."

until aws dynamodb list-tables \
    --endpoint-url "$DYNAMODB_ENDPOINT" \
    >/dev/null 2>&1
do
    echo "DynamoDB is not ready yet..."
    sleep 2
done

echo "DynamoDB is ready."
echo


# --------------------------------------------------
# Function: create table
# --------------------------------------------------

create_table() {
    local table_name="$1"
    shift

    echo "Creating table: $table_name"

    if aws dynamodb describe-table \
        --table-name "$table_name" \
        --endpoint-url "$DYNAMODB_ENDPOINT" \
        >/dev/null 2>&1
    then
        echo "Table already exists: $table_name"
        echo
        return
    fi

    aws dynamodb create-table \
        --table-name "$table_name" \
        "$@" \
        --billing-mode PAY_PER_REQUEST \
        --endpoint-url "$DYNAMODB_ENDPOINT"

    echo "Table created: $table_name"
    echo
}


# --------------------------------------------------
# ticketing-events
# --------------------------------------------------

create_table \
    "ticketing-events" \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH


# --------------------------------------------------
# ticketing-inventory
# --------------------------------------------------

create_table \
    "ticketing-inventory" \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH


# --------------------------------------------------
# ticketing-tickets
# --------------------------------------------------

create_table \
    "ticketing-tickets" \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=ticketId,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH \
        AttributeName=ticketId,KeyType=RANGE


# --------------------------------------------------
# ticketing-orders
# --------------------------------------------------

create_table \
    "ticketing-orders" \
    --attribute-definitions \
        AttributeName=orderId,AttributeType=S \
        AttributeName=idempotencyKey,AttributeType=S \
    --key-schema \
        AttributeName=orderId,KeyType=HASH \
    --global-secondary-indexes \
        'IndexName=idempotency-index,KeySchema=[{AttributeName=idempotencyKey,KeyType=HASH}],Projection={ProjectionType=ALL}'


# --------------------------------------------------
# Wait for tables
# --------------------------------------------------

echo "=========================================="
echo " Waiting for tables"
echo "=========================================="

TABLES=(
    "ticketing-events"
    "ticketing-inventory"
    "ticketing-tickets"
    "ticketing-orders"
)

for table in "${TABLES[@]}"
do
    echo "Waiting for $table..."

    aws dynamodb wait table-exists \
        --table-name "$table" \
        --endpoint-url "$DYNAMODB_ENDPOINT"

    echo "$table is ready."
done

echo


# --------------------------------------------------
# Show tables
# --------------------------------------------------

echo "=========================================="
echo " DynamoDB tables"
echo "=========================================="

aws dynamodb list-tables \
    --endpoint-url "$DYNAMODB_ENDPOINT"

echo
echo "=========================================="
echo " DynamoDB initialization completed"
echo "=========================================="