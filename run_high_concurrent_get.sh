#!/bin/bash
echo "🚀 High Concurrent GET Performance Test (10GB Memory + 10M Users)"
echo "=========================================="

echo "📊 Test Configuration:"
echo "• Concurrent users: 50 threads"
echo "• Test duration: 60 seconds"
echo "• Request type: 100% GET /api/owners/{random ID}"
echo "• ID range: 1-10,000,000"
echo "• Target data: 10M users, ~10GB memory"
echo ""

echo "📋 Pre-flight Checks:"
echo "1. Ensuring Spring Boot application is running..."
if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "❌ Application is not running, please start it first: java -Xmx12g -Xms2g -jar target/spring-petclinic-*.jar"
    exit 1
fi
echo "✅ Application is running normally"

echo ""
echo "2. Checking if data has been generated..."
if ! curl -s "http://localhost:8080/api/owners/10000000" | grep -q '"id":10000000'; then
    echo "❌ Data not generated, please run data generation first: ./generate_10m_memory.sh"
    exit 1
fi
echo "✅ Data generation completed (10M users)"

echo ""
echo "3. Checking if JMeter is installed..."
if ! command -v jmeter &> /dev/null; then
    echo "❌ JMeter is not installed, please install Apache JMeter first"
    echo "   Download: https://jmeter.apache.org/download_jmeter.cgi"
    exit 1
fi
echo "✅ JMeter is installed"

echo ""
echo "🎯 Starting high concurrent GET test..."
echo "Test will run for 60 seconds, will generate a large number of requests..."
echo ""

# Clean up old test results
rm -rf high_concurrent_get_report high_concurrent_get_results.jtl

# Run JMeter test
jmeter -n -t petclinic_get_only.jmx \
       -l high_concurrent_get_results.jtl \
       -e -o ./high_concurrent_get_report \
       -Jduration=60 \
       -Jthreads=50

echo ""
echo "🎉 High concurrent GET test completed!"
echo "📊 Results file: high_concurrent_get_results.jtl"
echo "📈 HTML report: ./high_concurrent_get_report/index.html"

# Results statistics
if [ -f "high_concurrent_get_results.jtl" ]; then
    echo ""
    echo "📈 Performance Statistics:"

    TOTAL_REQUESTS=$(wc -l < high_concurrent_get_results.jtl)
    SUCCESS_COUNT=$(awk -F',' '$4 == 200 {count++} END {print count}' high_concurrent_get_results.jtl)
    SUCCESS_RATE=$(echo "scale=2; $SUCCESS_COUNT * 100 / $TOTAL_REQUESTS" | bc 2>/dev/null || echo "0")

    echo "• Total requests: $TOTAL_REQUESTS"
    echo "• Successful requests: $SUCCESS_COUNT"
    echo "• Success rate: ${SUCCESS_RATE}%"

    if [ -f "high_concurrent_get_report/statistics.json" ]; then
        AVG_RESPONSE_TIME=$(grep -A 10 '"Total"' high_concurrent_get_report/statistics.json | grep '"meanResTime"' | head -1 | sed 's/.*: \([0-9.]*\).*/\1/' 2>/dev/null || echo "N/A")
        THROUGHPUT=$(grep -A 10 '"Total"' high_concurrent_get_report/statistics.json | grep '"throughput"' | head -1 | sed 's/.*: \([0-9.]*\).*/\1/' 2>/dev/null || echo "N/A")

        if [ "$AVG_RESPONSE_TIME" != "N/A" ]; then
            echo "• Average response time: ${AVG_RESPONSE_TIME}ms"
        fi
        if [ "$THROUGHPUT" != "N/A" ]; then
            echo "• Throughput: ${THROUGHPUT} req/s"
        fi
    fi
fi

echo ""
echo "🔍 Test Key Points:"
echo "• Complete object graphs for 10M users stored in memory"
echo "• Each GET request requires serialization of complex object structures"
echo "• Testing comprehensive performance of memory storage + object serialization"
echo "• Validating high concurrent read performance under 10GB memory"

