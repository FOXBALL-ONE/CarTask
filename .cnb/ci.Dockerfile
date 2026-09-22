FROM node:22-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates gnupg wget \
    && wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg \
    && echo "deb https://packages.adoptium.net/artifactory/deb bookworm main" \
        > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-25-jdk \
    && npm install --global pnpm@10 \
    && apt-get purge -y --auto-remove gnupg wget \
    && rm -rf /var/lib/apt/lists/*

RUN java -version \
    && node --version \
    && pnpm --version
