#!/bin/bash
echo "🚀 内存直接生成10,000,000个用户 (绕过网络栈)"
echo "==============================================="

API_URL="http://localhost:8080/api/owners/generate/10000000"

echo "🎯 开始内存直接生成..."
echo "• 目标数量: 10,000,000个用户"
echo "• 预期内存: ~10GB"
echo "• 生成方式: 多线程并行 (服务端内部)"
echo "• 绕过: HTTP协议栈、Spring框架、JSON序列化"
echo ""

start_time=$(date +%s)
echo "⏰ 开始时间: $(date)"

# 发送生成请求
response=$(curl -s "$API_URL")

end_time=$(date +%s)
echo "⏰ 结束时间: $(date)"

echo ""
echo "📊 生成结果:"
echo "$response"

# 计算总耗时
duration=$((end_time - start_time))
echo ""
echo "⏱️ 总耗时: ${duration}秒"

# 估算性能
if echo "$response" | grep -q "speed"; then
    speed=$(echo "$response" | grep -o '"speed":"[^"]*"' | cut -d'"' -f4)
    echo "⚡ 生成速度: $speed"
fi

echo ""
echo "🎯 性能优势:"
echo "• 零网络延迟"
echo "• 零HTTP协议开销"
echo "• 零Spring MVC开销"
echo "• 零JSON序列化开销"
echo "• 直接内存到内存"

echo ""
echo "🚀 接下来可以运行GET性能测试:"
echo "jmeter -n -t petclinic_get_only.jmx -l get_results.jtl -e -o ./get_report -Jduration=30 -Jthreads=50"
