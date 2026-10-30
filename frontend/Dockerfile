# Stage 1: Build React TypeScript frontend with Vite
FROM node:22-alpine AS builder

WORKDIR /app

# Install dependencies with clean cache
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build production bundle
COPY . .
RUN npm run build

# Stage 2: Serve static bundle via Caddy with HTTP/2 and zstd compression
FROM caddy:2-alpine AS runner

# Copy production static assets
COPY --from=builder /app/dist /srv

# Default internal Caddyfile (can be overridden by mounting /etc/caddy/Caddyfile in docker-compose)
COPY Caddyfile /etc/caddy/Caddyfile

EXPOSE 80

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
