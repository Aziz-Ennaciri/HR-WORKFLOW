FROM mcr.microsoft.com/devcontainers/java:17

# Install extra tools if needed
RUN apt-get update && apt-get install -y \
    git \
    curl

RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs


WORKDIR /workspace
