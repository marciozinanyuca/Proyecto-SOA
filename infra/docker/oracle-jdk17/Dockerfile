# Exact Java runtime required by the project. No Oracle registry login needed.
FROM oraclelinux:8-slim@sha256:3a1318bf9606038d8056f765243a050bdd9473b9bc4f049169ce3b1fb407f6b5

RUN microdnf install -y ca-certificates curl gzip tar \
    && microdnf clean all

ENV JAVA_HOME=/opt/java/jdk
ENV PATH="/opt/java/jdk/bin:${PATH}"

RUN curl --fail --silent --show-error --proto '=https' --tlsv1.2 --max-time 300 \
      --output /tmp/jdk.tar.gz \
      https://download.oracle.com/java/17/archive/jdk-17.0.12_linux-x64_bin.tar.gz \
    && echo '311f1448312ecab391fe2a1b2ac140d6e1c7aea6fbf08416b466a58874f2b40f  /tmp/jdk.tar.gz' | sha256sum --check --strict \
    && mkdir -p "$JAVA_HOME" \
    && tar --extract --gzip --file /tmp/jdk.tar.gz --directory "$JAVA_HOME" --strip-components=1 \
    && rm /tmp/jdk.tar.gz \
    && java -version 2>&1 | grep -F '17.0.12' \
    && java -XshowSettings:properties -version 2>&1 | grep -F 'java.vendor = Oracle Corporation'

USER 10001:10001
CMD ["java", "-version"]
