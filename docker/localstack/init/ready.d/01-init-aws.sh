#!/bin/sh
set -e

awslocal sqs create-queue --queue-name orders
awslocal sqs create-queue --queue-name orders-dlq
