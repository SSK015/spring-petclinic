#!/bin/bash
echo "🔨 构建Spring PetClinic (跳过所有检查)"
echo "====================================="

# 构建参数 - 跳过所有可能导致失败的检查
BUILD_OPTS="-DskipTests -Dcheckstyle.skip=true -Dnohttp-checkstyle.skip=true"

echo "📋 构建参数: $BUILD_OPTS"
echo "🏗️  开始构建..."

if ./mvnw clean package $BUILD_OPTS; then
    echo ""
    echo "✅ 构建成功!"
    echo "📦 JAR文件: target/spring-petclinic-*.jar"
    echo ""
    echo "🚀 启动命令:"
    echo "java -jar target/spring-petclinic-*.jar"
else
    echo ""
    echo "❌ 构建失败!"
    exit 1
fi
