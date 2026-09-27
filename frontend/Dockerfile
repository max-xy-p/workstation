FROM node:20-alpine AS builder

WORKDIR /build

COPY clients/typescript ./clients/typescript
COPY frontend/package.json frontend/package-lock.json ./frontend/

WORKDIR /build/frontend
RUN npm ci --ignore-scripts

COPY frontend/ ./

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/frontend/dist /usr/share/nginx/html

USER nginx

EXPOSE 8080
