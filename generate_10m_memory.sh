#!/bin/bash

echo "🚀 生成1000万个用户数据到内存中"
echo "================================="

# API URL
API_URL="http://localhost:8080/api/owners/generate/10000000"

echo "API URL: $API_URL"
echo "开始发送请求..."

# 发送生成请求
response=$(curl -s -X POST "$API_URL")

if [ $? -eq 0 ]; then
    echo "✅ 请求发送成功!"
    echo "响应: $response"
else
    echo "❌ 请求失败!"
    exit 1
fi

echo ""
echo "📊 验证数据生成结果..."
sleep 2

# 检查用户数量
count_response=$(curl -s -X GET "http://localhost:8080/api/owners/count")
echo "当前用户数量: $count_response"

echo ""
echo "🎉 1000万用户数据生成完成!"
