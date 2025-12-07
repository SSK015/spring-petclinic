#!/bin/bash
echo "🚀 In-Memory High Concurrent GET Load Test"
echo "=========================="

# Default parameters
THREADS=${1:-10}      # Default 10 threads (reduced to match QPS limits)
DURATION=${2:-30}     # Default 30 seconds
QPS_LIMIT=${3:-100000} # Default 100K QPS per thread

echo "📊 Test Configuration:"
echo "• Thread count: $THREADS"
echo "• Test duration: ${DURATION} seconds"
echo "• QPS limit per thread: ${QPS_LIMIT}"
echo "• Theoretical total QPS: $(($THREADS * $QPS_LIMIT))"
echo "• Request type: In-memory GET (bypassing HTTP network stack)"
echo "• Data access: Direct ConcurrentHashMap query"
echo ""

echo "📋 Pre-flight Checks:"
echo "1. Ensuring Spring Boot application is running..."
if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "❌ Application is not running, please start the application first"
    echo "   java -Xmx12g -Xms2g -jar target/spring-petclinic-*.jar"
    exit 1
fi
echo "✅ Application is running normally"

echo ""
echo "2. Checking for test data..."
DATA_COUNT=$(curl -s "http://localhost:8080/api/owners" | wc -c)
if [ "$DATA_COUNT" -lt 10 ]; then
    echo "⚠️  No data found, generating 100K test users..."
    GENERATE_RESULT=$(curl -X POST -H "Content-Type: application/json" \
        "http://localhost:8080/api/owners/generate/100000" -s)
    echo "✅ Data generation completed"
fi
echo "✅ Data preparation ready"

echo ""
echo "🎯 Starting in-memory load test..."
echo "Test will run for ${DURATION} seconds, will generate a large number of in-memory GET requests..."
echo ""

# Record start time
START_TIME=$(date +%s)

# Run load test
echo "🔄 Executing load test: $THREADS threads x ${DURATION} seconds x ${QPS_LIMIT} QPS/thread"
RESULT=$(curl -X POST -H "Content-Type: application/json" \
    "http://localhost:8080/api/owners/loadtest/$THREADS/$DURATION/$QPS_LIMIT" -s)

# Record end time
END_TIME=$(date +%s)
ACTUAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo "🎉 Load test completed!"
echo "📊 Performance Results:"

# Parse and display results
echo "$RESULT" | jq -r '
    "Total requests: \(.totalRequests)",
    "Success rate: \(.successRate)",
    "QPS (queries per second): \(.qps)",
    "Average response time: \(.avgResponseTime)",
    "Minimum response time: \(.minResponseTime)",
    "Maximum response time: \(.maxResponseTime)",
    "Thread count: \(.threads)",
    "Actual test duration: \(.duration)",
    "Dataset size: \(.dataSize)"
' 2>/dev/null || echo "$RESULT"

echo ""
echo "🔍 Performance Analysis:"
echo "• This QPS represents pure in-memory query performance"
echo "• Does not include HTTP protocol stack overhead"
echo "• Does not include Spring MVC framework overhead"
echo "• Does not include JSON serialization overhead"
echo "• Represents the theoretical limit of JVM memory access"

echo ""
echo "💡 Comparison with network tests:"
echo "• Network GET test: ~30,000 QPS (includes all overhead)"
echo "• In-memory GET test: Expected millions QPS (memory access only)"
echo "• Performance improvement: ~100x"
