# syntax=docker/dockerfile:1.7
# ---------- build: Angular on Node 24 ----------
FROM node:24-alpine AS build
WORKDIR /app

# Optional corporate npm registry (e.g. an Artifactory npm remote) for networks that block npmjs
ARG NPM_REGISTRY=""
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi \
 && npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---------- runtime: nginx serves the SPA and proxies /api to the backend ----------
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY proxy-backend.conf /etc/nginx/proxy-backend.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD wget -qO /dev/null http://127.0.0.1/ || exit 1
