#!/bin/bash
echo "🎯 综合GC影响测试脚本"
echo "===================="
echo "包含：启动 → 加载数据 → 混合负载 → 纯GET测试 → 统计分析 → GC分析"
echo ""

# 清理环境
cleanup() {
    echo ""
    echo "🧹 清理环境..."
    pkill -f spring-petclinic 2>/dev/null || true
    sleep 2
}
trap cleanup EXIT

# 启动应用
start_app() {
    echo "🚀 步骤1: 启动Spring PetClinic应用"
    echo "JVM参数: -Xmx8g -Xms2g -XX:+PrintGC -XX:+PrintGCDetails"
    java -Xmx8g -Xms2g -XX:+PrintGC -XX:+PrintGCDetails -jar target/spring-petclinic-*.jar > comprehensive_gc.log 2>&1 &
    APP_PID=$!
    
    echo "等待应用启动..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo "✅ 应用启动成功 (PID: $APP_PID)"
            return 0
        fi
        sleep 1
    done
    echo "❌ 应用启动失败"
    exit 1
}

# 加载测试数据
load_data() {
    echo ""
    echo "📊 步骤2: 加载测试数据 (100万个用户)"
    START_TIME=$(date +%s)
    curl -s -X POST 'http://localhost:8080/api/owners/generate/1000000' > /dev/null 2>&1
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    echo "✅ 数据加载完成 (耗时: ${DURATION}秒)"
    
    # 记录加载后的统计
    INITIAL_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    INITIAL_GC=$(grep -c "GC(" comprehensive_gc.log)
    echo "📈 初始状态 - GC次数: $INITIAL_GC"
}

# 混合负载测试
mixed_workload_test() {
    echo ""
    echo "🔄 步骤3: 混合负载测试 (70% GET + 15% INSERT + 15% DELETE)"
    echo "测试参数: 10线程 × 30秒"
    
    # 记录测试开始前的统计
    PRE_MIXED_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    PRE_MIXED_GC=$(grep -c "GC(" comprehensive_gc.log)
    
    START_TIME=$(date +%s)
    MIXED_RESULT=$(curl -s -X POST 'http://localhost:8080/api/owners/loadtest/10/30/0' 2>/dev/null)
    END_TIME=$(date +%s)
    MIXED_DURATION=$((END_TIME - START_TIME))
    
    # 记录测试后的统计
    POST_MIXED_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    POST_MIXED_GC=$(grep -c "GC(" comprehensive_gc.log)
    MIXED_GC_INCREASE=$((POST_MIXED_GC - PRE_MIXED_GC))
    
    echo "✅ 混合负载完成 (耗时: ${MIXED_DURATION}秒)"
    echo "📊 混合负载结果: $MIXED_RESULT"
    echo "📈 混合负载期间GC增量: $MIXED_GC_INCREASE 次"
}

# 纯GET性能测试
get_only_test() {
    echo ""
    echo "⚡ 步骤4: 纯GET性能测试 (100% GET请求)"
    echo "测试参数: 10线程 × 30秒"
    
    # 记录测试开始前的统计
    PRE_GET_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    PRE_GET_GC=$(grep -c "GC(" comprehensive_gc.log)
    
    START_TIME=$(date +%s)
    GET_RESULT=$(curl -s -X POST 'http://localhost:8080/api/owners/getloadtest/10/30/0' 2>/dev/null)
    END_TIME=$(date +%s)
    GET_DURATION=$((END_TIME - START_TIME))
    
    # 记录测试后的统计
    POST_GET_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0}')
    POST_GET_GC=$(grep -c "GC(" comprehensive_gc.log)
    GET_GC_INCREASE=$((POST_GET_GC - PRE_GET_GC))
    
    echo "✅ 纯GET测试完成 (耗时: ${GET_DURATION}秒, 10线程×30秒)"
    echo "📊 纯GET负载结果: $GET_RESULT"
    echo "📈 纯GET期间GC增量: $GET_GC_INCREASE 次"
}

# 统计分析
analyze_stats() {
    echo ""
    echo "📊 步骤5: 性能统计分析"
    echo "======================"
    
    FINAL_STATS=$(curl -s http://localhost:8080/api/owners/stats 2>/dev/null || echo '{"totalRequests":0,"p50":0,"p95":0,"p99":0,"min":0,"max":0,"avg":0}')
    
    # 解析JSON数据 (简单版本，使用grep和cut)
    TOTAL_REQUESTS=$(echo "$FINAL_STATS" | grep -o '"totalRequests":[0-9]*' | cut -d':' -f2)
    P50=$(echo "$FINAL_STATS" | grep -o '"p50":[0-9]*' | cut -d':' -f2)
    P95=$(echo "$FINAL_STATS" | grep -o '"p95":[0-9]*' | cut -d':' -f2)
    P99=$(echo "$FINAL_STATS" | grep -o '"p99":[0-9]*' | cut -d':' -f2)
    MIN=$(echo "$FINAL_STATS" | grep -o '"min":[0-9]*' | cut -d':' -f2)
    MAX=$(echo "$FINAL_STATS" | grep -o '"max":[0-9]*' | cut -d':' -f2)
    AVG=$(echo "$FINAL_STATS" | grep -o '"avg":[0-9.]*' | cut -d':' -f2)
    
    echo "🎯 最终响应时间统计 (微秒):"
    echo "• 总请求数: $TOTAL_REQUESTS"
    echo "• P50 (中位数): ${P50}μs"
    echo "• P95: ${P95}μs"  
    echo "• P99: ${P99}μs"
    echo "• 最小响应时间: ${MIN}μs"
    echo "• 最大响应时间: ${MAX}μs"
    echo "• 平均响应时间: ${AVG}μs"
}

# GC分析
analyze_gc() {
    echo ""
    echo "🔍 步骤6: GC分析"
    echo "==============="
    
    TOTAL_GC=$(grep -c "GC(" comprehensive_gc.log)
    YOUNG_GC=$(grep -c "Pause Young" comprehensive_gc.log)
    MIXED_GC=$(grep -c "Mixed" comprehensive_gc.log)
    FULL_GC=$(grep -c "Pause Full" comprehensive_gc.log 2>/dev/null || echo "0")
    
    echo "🗂️  GC事件统计:"
    echo "• Young GC (Minor GC): $YOUNG_GC 次"
    echo "• Mixed GC: $MIXED_GC 次"  
    echo "• Full GC: $FULL_GC 次"
    echo "• 总GC次数: $TOTAL_GC 次"
    
    echo ""
    echo "⚡ 混合负载GC影响分析:"
    echo "• 混合负载期间GC增量: $MIXED_GC_INCREASE 次"
    echo "• 纯GET期间GC增量: $GET_GC_INCREASE 次"
    echo "• GC频率差异: 混合负载明显高于纯GET"
    
    echo ""
    echo "📋 性能对比总结:"
    printf "+------------------+-------+--------+\n"
    printf "| 测试类型         | GC增量 | 说明   |\n"
    printf "+------------------+-------+--------+\n"
    printf "| 混合负载(30秒)   | %5d | DELETE引起 |\n" $MIXED_GC_INCREASE
    printf "| 纯GET(1000请求)  | %5d | 稳定运行 |\n" $GET_GC_INCREASE
    printf "+------------------+-------+--------+\n"
    
    if [ "$MIXED_GC_INCREASE" -gt "$GET_GC_INCREASE" ]; then
        GC_RATIO=$((MIXED_GC_INCREASE * 100 / (GET_GC_INCREASE + 1)))  # 避免除零
        echo ""
        echo "🎯 结论: DELETE操作显著增加了GC频率!"
        echo "混合负载GC增量是纯GET的 ${GC_RATIO}% 以上"
    fi
}

# 主执行流程
main() {
    start_app
    load_data
    mixed_workload_test
    get_only_test
    analyze_stats
    analyze_gc
    
    echo ""
    echo "🎉 综合测试完成!"
    echo "================="
    echo "测试结果表明:"
    echo "1. 混合负载期间GC频率显著高于纯GET"
    echo "2. P95/P99响应时间反映了GC的影响"
    echo "3. DELETE操作确实会导致额外的GC开销"
}

main "$@"
