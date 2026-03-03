# Production Runtime: Alpine Linux with Corretto 21
FROM amazoncorretto:21-alpine

# Install dependencies for Aeron and networking
RUN apk add --no-cache bash procps iproute2

WORKDIR /app

# Copy the fat JAR built by shadowJar
COPY jvm/build/libs/jvm-1.0-SNAPSHOT-all.jar app.jar

# Define default JVM options
ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED \
               --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
               --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
               --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
               --add-opens java.base/java.lang=ALL-UNNAMED \
               --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
               --add-opens java.base/java.io=ALL-UNNAMED \
               --add-opens java.base/java.util=ALL-UNNAMED \
               --add-opens java.base/java.nio=ALL-UNNAMED \
               --add-opens java.base/sun.nio.ch=ALL-UNNAMED"

# Use -DAERON_CHANNEL to override the static constant definitely
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Daeron.dir=/dev/shm/aeron -DAERON_CHANNEL=$AERON_CHANNEL -cp app.jar $MAIN_CLASS $ARGS"]
