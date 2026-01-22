FROM mcr.microsoft.com/devcontainers/java:17

# Install extra tools if needed
RUN apt-get update && apt-get install -y \
    git \
    curl

WORKDIR /workspace