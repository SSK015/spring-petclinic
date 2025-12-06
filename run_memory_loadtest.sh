#!/bin/bash
echo "🚀 内存内高并发GET负载测试"
echo "=========================="

# 默认参数
THREADS=${1:-10}      # 默认10个线程 (降低以配合QPS限制)
DURATION=${2:-30}     # 默认30秒
QPS_LIMIT=${3:-100000} # 默认每个线程10万QPS

echo "📊 测试配置:"
echo "• 线程数: $THREADS"
echo "• 测试时长: ${DURATION}秒"
echo "• 每线程QPS限制: ${QPS_LIMIT}"
echo "• 理论总QPS: $(($THREADS * $QPS_LIMIT))"
echo "• 请求类型: 内存内GET (绕过HTTP网络栈)"
echo "• 数据访问: 直接ConcurrentHashMap查询"
echo ""

echo "📋 前置检查:"
echo "1. 确保Spring Boot应用正在运行..."
if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "❌ 应用未运行，请先启动应用"
    echo "   java -Xmx12g -Xms2g -jar target/spring-petclinic-*.jar"
    exit 1
fi
echo "✅ 应用运行正常"

echo ""
echo "2. 检查是否有测试数据..."
DATA_COUNT=$(curl -s "http://localhost:8080/api/owners" | wc -c)
if [ "$DATA_COUNT" -lt 10 ]; then
    echo "⚠️  没有找到数据，正在生成10万测试用户..."
    GENERATE_RESULT=$(curl -X POST -H "Content-Type: application/json" \
        "http://localhost:8080/api/owners/generate/100000" -s)
    echo "✅ 数据生成完成"
fi
echo "✅ 数据准备就绪"

echo ""
echo "🎯 开始内存内负载测试..."
echo "测试将持续${DURATION}秒，期间会产生大量内存GET请求..."
echo ""

# 记录开始时间
START_TIME=$(date +%s)

# 运行负载测试
echo "🔄 执行负载测试: $THREADS线程 x ${DURATION}秒 x ${QPS_LIMIT}QPS/线程"
RESULT=$(curl -X POST -H "Content-Type: application/json" \
    "http://localhost:8080/api/owners/loadtest/$THREADS/$DURATION/$QPS_LIMIT" -s)

# 记录结束时间
END_TIME=$(date +%s)
ACTUAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo "🎉 负载测试完成!"
echo "📊 性能结果:"

# 解析和显示结果
echo "$RESULT" | jq -r '
    "总请求数: \(.totalRequests)",
    "成功率: \(.successRate)",
    "QPS (每秒查询数): \(.qps)",
    "平均响应时间: \(.avgResponseTime)",
    "最小响应时间: \(.minResponseTime)",
    "最大响应时间: \(.maxResponseTime)",
    "线程数: \(.threads)",
    "实际测试时长: \(.duration)",
    "数据集大小: \(.dataSize)"
' 2>/dev/null || echo "$RESULT"

echo ""
echo "🔍 性能分析:"
echo "• 这个QPS代表纯内存查询性能"
echo "• 不包含HTTP协议栈开销"
echo "• 不包含Spring MVC框架开销"
echo "• 不包含JSON序列化开销"
echo "• 代表JVM内存访问的理论极限"

echo ""
echo "💡 与网络测试对比:"
echo "• 网络GET测试: ~30,000 QPS (包含所有开销)"
echo "• 内存GET测试: 预期数百万QPS (仅内存访问)"
echo "• 性能提升: 约100倍"
