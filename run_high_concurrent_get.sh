#!/bin/bash
echo "🚀 高并发GET性能测试 (10GB内存 + 10M用户)"
echo "=========================================="

echo "📊 测试配置:"
echo "• 并发用户数: 50个线程"
echo "• 测试持续时间: 60秒"
echo "• 请求类型: 100% GET /api/owners/{随机ID}"
echo "• ID范围: 1-10,000,000"
echo "• 目标数据: 10M用户，~10GB内存"
echo ""

echo "📋 前置检查:"
echo "1. 确保Spring Boot应用正在运行..."
if ! curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "❌ 应用未运行，请先启动: java -Xmx12g -Xms2g -jar target/spring-petclinic-*.jar"
    exit 1
fi
echo "✅ 应用运行正常"

echo ""
echo "2. 检查数据是否已生成..."
if ! curl -s "http://localhost:8080/api/owners/10000000" | grep -q '"id":10000000'; then
    echo "❌ 数据未生成，请先运行数据生成: ./generate_10m_memory.sh"
    exit 1
fi
echo "✅ 数据生成完成 (10M用户)"

echo ""
echo "3. 检查JMeter是否安装..."
if ! command -v jmeter &> /dev/null; then
    echo "❌ JMeter未安装，请先安装Apache JMeter"
    echo "   下载: https://jmeter.apache.org/download_jmeter.cgi"
    exit 1
fi
echo "✅ JMeter已安装"

echo ""
echo "🎯 开始高并发GET测试..."
echo "测试将持续60秒，期间会产生大量请求..."
echo ""

# 清理旧的测试结果
rm -rf high_concurrent_get_report high_concurrent_get_results.jtl

# 运行JMeter测试
jmeter -n -t petclinic_get_only.jmx \
       -l high_concurrent_get_results.jtl \
       -e -o ./high_concurrent_get_report \
       -Jduration=60 \
       -Jthreads=50

echo ""
echo "🎉 高并发GET测试完成!"
echo "📊 结果文件: high_concurrent_get_results.jtl"
echo "📈 HTML报告: ./high_concurrent_get_report/index.html"

# 结果统计
if [ -f "high_concurrent_get_results.jtl" ]; then
    echo ""
    echo "📈 性能统计:"

    TOTAL_REQUESTS=$(wc -l < high_concurrent_get_results.jtl)
    SUCCESS_COUNT=$(awk -F',' '$4 == 200 {count++} END {print count}' high_concurrent_get_results.jtl)
    SUCCESS_RATE=$(echo "scale=2; $SUCCESS_COUNT * 100 / $TOTAL_REQUESTS" | bc 2>/dev/null || echo "0")

    echo "• 总请求数: $TOTAL_REQUESTS"
    echo "• 成功请求数: $SUCCESS_COUNT"
    echo "• 成功率: ${SUCCESS_RATE}%"

    if [ -f "high_concurrent_get_report/statistics.json" ]; then
        AVG_RESPONSE_TIME=$(grep -A 10 '"Total"' high_concurrent_get_report/statistics.json | grep '"meanResTime"' | head -1 | sed 's/.*: \([0-9.]*\).*/\1/' 2>/dev/null || echo "N/A")
        THROUGHPUT=$(grep -A 10 '"Total"' high_concurrent_get_report/statistics.json | grep '"throughput"' | head -1 | sed 's/.*: \([0-9.]*\).*/\1/' 2>/dev/null || echo "N/A")

        if [ "$AVG_RESPONSE_TIME" != "N/A" ]; then
            echo "• 平均响应时间: ${AVG_RESPONSE_TIME}ms"
        fi
        if [ "$THROUGHPUT" != "N/A" ]; then
            echo "• 吞吐量: ${THROUGHPUT} req/s"
        fi
    fi
fi

echo ""
echo "🔍 测试要点:"
echo "• 内存中存储了10M用户的完整对象图"
echo "• 每次GET请求都要序列化复杂的对象结构"
echo "• 测试内存存储 + 对象序列化的综合性能"
echo "• 验证10GB内存下的高并发读取性能"

