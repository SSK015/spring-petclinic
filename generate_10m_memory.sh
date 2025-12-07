#!/bin/bash

echo "🚀 Generating 10 million user data into memory"
echo "================================="

# API URL
API_URL="http://localhost:8080/api/owners/generate/10000000"

echo "API URL: $API_URL"
echo "Starting to send request..."

# Send generation request
response=$(curl -s -X POST "$API_URL")

if [ $? -eq 0 ]; then
    echo "✅ Request sent successfully!"
    echo "Response: $response"
else
    echo "❌ Request failed!"
    exit 1
fi

echo ""
echo "📊 Verifying data generation results..."
sleep 2

# Check user count
count_response=$(curl -s -X GET "http://localhost:8080/api/owners/count")
echo "Current user count: $count_response"

echo ""
echo "🎉 10 million user data generation completed!"
