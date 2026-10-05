#!/bin/sh

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "Create event:"
curl -s -X POST "$BASE_URL/api/v1/events" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Nequi Live 2026",
    "date": "2026-12-15T20:00:00Z",
    "venue": "Bogota Arena",
    "totalCapacity": 100
  }'

echo
echo "Create order:"
echo "Replace EVENT_ID with the created event id."
curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{
    "eventId": "EVENT_ID",
    "quantity": 2
  }'

echo
echo "Availability:"
curl -s "$BASE_URL/api/v1/events/EVENT_ID/availability"
