FROM node:22.16.0-alpine@sha256:41e4389f3d988d2ed55392df4db1420ad048ae53324a8e2b7c6d19508288107e AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig*.json vite.config.ts ./
COPY src ./src
# Public build configuration only. No SENACE key or backend private FQDN.
ENV VITE_API_MODE=http
ENV VITE_API_BASE_URL=same-origin
ENV VITE_HTTP_FEATURES=monitor,consultoras,dashboard,explorador
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine@sha256:7377697a821c131a924a7105fafbe7414db4e9fcc77a6f08f776f33f141ec3f8 AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
ENV NGINX_ENVSUBST_FILTER=BACKEND_FQDN
EXPOSE 8080
