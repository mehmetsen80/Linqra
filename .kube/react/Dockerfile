# ========================================================
# Stage 1: Build React App
# ========================================================
FROM node:21.5.0-slim as builder

# Set working directory for the build to match source layout
WORKDIR /app

# Install dependencies
# COPY from edge-service/ since build context is Root
COPY edge-service/package*.json ./
RUN npm install --legacy-peer-deps

# Copy source code (from edge-service)
COPY edge-service/ .

# Build for production
# Inject Environment Variables at Build Time
ARG NODE_ENV=production
ENV NODE_ENV=$NODE_ENV

ARG REACT_APP_KEYCLOAK_URL
ARG REACT_APP_KEYCLOAK_REALM
ARG REACT_APP_KEYCLOAK_CLIENT_ID

ENV REACT_APP_KEYCLOAK_URL=$REACT_APP_KEYCLOAK_URL
ENV REACT_APP_KEYCLOAK_REALM=$REACT_APP_KEYCLOAK_REALM
ENV REACT_APP_KEYCLOAK_CLIENT_ID=$REACT_APP_KEYCLOAK_CLIENT_ID

RUN npm run build

# ========================================================
# Stage 2: Serve with Nginx
# ========================================================
FROM nginx:alpine

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy built artifacts
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
# Path relative to Project Root
COPY .kube/react/nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
