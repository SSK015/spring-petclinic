#!/bin/bash
echo "🔨 Building Spring PetClinic (skipping all checks)"
echo "====================================="

# Build parameters - skip all checks that might cause failure
BUILD_OPTS="-DskipTests -Dcheckstyle.skip=true -Dnohttp-checkstyle.skip=true"

echo "📋 Build parameters: $BUILD_OPTS"
echo "🏗️  Starting build..."

if ./mvnw clean package $BUILD_OPTS; then
    echo ""
    echo "✅ Build successful!"
    echo "📦 JAR file: target/spring-petclinic-*.jar"
    echo ""
    echo "🚀 Start command:"
    echo "java -jar target/spring-petclinic-*.jar"
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
