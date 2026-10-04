FROM node:22-alpine AS build
ARG VITE_SENTRY_DSN=
ARG VITE_APP_ENV=production
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN
ENV VITE_APP_ENV=$VITE_APP_ENV
# Fond de carte (vide = OpenStreetMap, voir src/mapTiles.js)
ARG VITE_MAP_TILE_URL=
ARG VITE_MAP_ATTRIBUTION=
ENV VITE_MAP_TILE_URL=$VITE_MAP_TILE_URL
ENV VITE_MAP_ATTRIBUTION=$VITE_MAP_ATTRIBUTION
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
