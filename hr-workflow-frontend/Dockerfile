FROM node:20-alpine

WORKDIR /app

RUN npm config set strict-ssl false

# Copy package files
COPY package.json package-lock.json* ./

# Clean install
RUN npm install

# Verify installation
RUN npm list next || echo "ERROR: next not installed"

# Copy everything
COPY . .

EXPOSE 3000

# Use npx to run next
CMD ["npx", "next", "dev"]