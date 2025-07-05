FROM zilliz/attu:v2.5.12

# Install debugging tools and git
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    netcat-openbsd \
    iputils-ping \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js dependencies for building
RUN yarn install

# Expose the port Attu runs on
EXPOSE 3000

# Start Attu
CMD ["yarn", "start:prod"]

