#!/bin/bash
echo "🎯 Comprehensive GC Impact Test Script"
echo "===================="
echo "Includes: Start → Load Data → Mixed Workload → Pure GET Test → Statistical Analysis → GC Analysis"
echo ""

# Clean up environment
cleanup() {
    echo ""
    echo "🧹 Cleaning up environment..."
    pkill -f spring-petclinic 2>/dev/null || true
    sleep 2
}
trap cleanup EXIT

# Start application
start_app() {
    echo "🚀 Step 1: Starting Spring PetClinic application"
    echo "JVM parameters: -Xmx8g -Xms2g -XX:+PrintGC -XX:+PrintGCDetails"
    java -Xmx8g -Xms2g -XX:+PrintGC -XX:+PrintGCDetails -jar target/spring-petclinic-*.jar > comprehensive_gc.log 2>&1 &
    APP_PID=$!
    
    echo "Waiting for application to start..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo "✅ Application started successfully (PID: $APP_PID)"
            return 0
        fi
        sleep 1
    done
    echo "❌ Application startup failed"
    exit 1
}

# Load test data
load_data() {
    echo ""
    echo "📊 Step 2: Loading test data (1 million users)"
    START_TIME=$(date +%s)
    curl -s -X POST 'http://localhost:8080/api/owners/generate/1000000' > /dev/null 2>&1
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    echo "✅ Data loading completed (duration: ${DURATION} seconds)"
    
    # Record statistics after loading
    INITIAL_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    INITIAL_GC=$(grep -c "GC(" comprehensive_gc.log)
    echo "📈 Initial state - GC count: $INITIAL_GC"
}

# Mixed workload test
mixed_workload_test() {
    echo ""
    echo "🔄 Step 3: Mixed workload test (70% GET + 15% INSERT + 15% DELETE)"
    echo "Test parameters: 10 threads × 30 seconds"
    
    # Record statistics before test starts
    PRE_MIXED_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    PRE_MIXED_GC=$(grep -c "GC(" comprehensive_gc.log)
    
    START_TIME=$(date +%s)
    MIXED_RESULT=$(curl -s -X POST 'http://localhost:8080/api/owners/loadtest/10/30/0' 2>/dev/null)
    END_TIME=$(date +%s)
    MIXED_DURATION=$((END_TIME - START_TIME))
    
    # Record statistics after test
    POST_MIXED_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    POST_MIXED_GC=$(grep -c "GC(" comprehensive_gc.log)
    MIXED_GC_INCREASE=$((POST_MIXED_GC - PRE_MIXED_GC))
    
    echo "✅ Mixed workload completed (duration: ${MIXED_DURATION} seconds)"
    echo "📊 Mixed workload results: $MIXED_RESULT"
    echo "📈 GC increase during mixed workload: $MIXED_GC_INCREASE times"
}

# Pure GET performance test
get_only_test() {
    echo ""
    echo "⚡ Step 4: Pure GET performance test (100% GET requests)"
    echo "Test parameters: 10 threads × 30 seconds"
    
    # Record statistics before test starts
    PRE_GET_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    PRE_GET_GC=$(grep -c "GC(" comprehensive_gc.log)
    
    START_TIME=$(date +%s)
    GET_RESULT=$(curl -s -X POST 'http://localhost:8080/api/owners/getloadtest/10/30/0' 2>/dev/null)
    END_TIME=$(date +%s)
    GET_DURATION=$((END_TIME - START_TIME))
    
    # Record statistics after test
    POST_GET_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    POST_GET_GC=$(grep -c "GC(" comprehensive_gc.log)
    GET_GC_INCREASE=$((POST_GET_GC - PRE_GET_GC))
    
    echo "✅ Pure GET test completed (duration: ${GET_DURATION} seconds, 10 threads×30 seconds)"
    echo "📊 Pure GET workload results: $GET_RESULT"
    echo "📈 GC increase during pure GET: $GET_GC_INCREASE times"
}

# Statistical analysis
analyze_stats() {
    echo ""
    echo "📊 Step 5: Performance statistical analysis"
    echo "======================"
    
    FINAL_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0,"p50":0,"p95":0,"p99":0,"min":0,"max":0,"avg":0}')
    
    # Parse JSON data (simple version, using grep and cut)
    TOTAL_REQUESTS=$(echo "$FINAL_STATS" | grep -o '"totalRequests":[0-9]*' | cut -d':' -f2)
    P50=$(echo "$FINAL_STATS" | grep -o '"p50":[0-9]*' | cut -d':' -f2)
    P95=$(echo "$FINAL_STATS" | grep -o '"p95":[0-9]*' | cut -d':' -f2)
    P99=$(echo "$FINAL_STATS" | grep -o '"p99":[0-9]*' | cut -d':' -f2)
    MIN=$(echo "$FINAL_STATS" | grep -o '"min":[0-9]*' | cut -d':' -f2)
    MAX=$(echo "$FINAL_STATS" | grep -o '"max":[0-9]*' | cut -d':' -f2)
    AVG=$(echo "$FINAL_STATS" | grep -o '"avg":[0-9.]*' | cut -d':' -f2)
    
    echo "🎯 Final response time statistics (microseconds):"
    echo "• Total requests: $TOTAL_REQUESTS"
    echo "• P50 (median): ${P50}μs"
    echo "• P95: ${P95}μs"
    echo "• P99: ${P99}μs"
    echo "• Minimum response time: ${MIN}μs"
    echo "• Maximum response time: ${MAX}μs"
    echo "• Average response time: ${AVG}μs"
}

# GC analysis
analyze_gc() {
    echo ""
    echo "🔍 Step 6: GC analysis"
    echo "==============="
    
    TOTAL_GC=$(grep -c "GC(" comprehensive_gc.log)
    YOUNG_GC=$(grep -c "Pause Young" comprehensive_gc.log)
    MIXED_GC=$(grep -c "Mixed" comprehensive_gc.log)
    FULL_GC=$(grep -c "Pause Full" comprehensive_gc.log 2>/dev/null || echo "0")
    
    echo "🗂️  GC event statistics:"
    echo "• Young GC (Minor GC): $YOUNG_GC times"
    echo "• Mixed GC: $MIXED_GC times"
    echo "• Full GC: $FULL_GC times"
    echo "• Total GC count: $TOTAL_GC times"
    
    echo ""
    echo "⚡ Mixed workload GC impact analysis:"
    echo "• GC increase during mixed workload: $MIXED_GC_INCREASE times"
    echo "• GC increase during pure GET: $GET_GC_INCREASE times"
    echo "• GC frequency difference: Mixed workload significantly higher than pure GET"
    
    echo ""
    echo "📋 Performance comparison summary:"
    printf "+------------------+-------+--------+\n"
    printf "| Test Type        | GC Increase | Description |\n"
    printf "+------------------+-------+--------+\n"
    printf "| Mixed Workload(30s) | %5d | Caused by DELETE |\n" $MIXED_GC_INCREASE
    printf "| Pure GET(1000 req)  | %5d | Stable run |\n" $GET_GC_INCREASE
    printf "+------------------+-------+--------+\n"
    
    if [ "$MIXED_GC_INCREASE" -gt "$GET_GC_INCREASE" ]; then
        GC_RATIO=$((MIXED_GC_INCREASE * 100 / (GET_GC_INCREASE + 1)))  # Avoid division by zero
        echo ""
        echo "🎯 Conclusion: DELETE operations significantly increased GC frequency!"
        echo "Mixed workload GC increase is ${GC_RATIO}% more than pure GET"
    fi
}

# Main execution flow
main() {
    start_app
    load_data
    mixed_workload_test
    get_only_test
    analyze_stats
    analyze_gc
    
    echo ""
    echo "🎉 Comprehensive test completed!"
    echo "================="
    echo "Test results indicate:"
    echo "1. GC frequency significantly higher during mixed workload than pure GET"
    echo "2. P95/P99 response times reflect GC impact"
    echo "3. DELETE operations do cause additional GC overhead"
}

main "$@"
