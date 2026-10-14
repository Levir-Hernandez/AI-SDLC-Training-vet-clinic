# Local convenience image (docker compose --profile app up). Not a production setup.
FROM node:22.20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Inlined at build time; the browser calls the backend through the host port.
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN npm run build

FROM node:22.20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build --chown=node:node /app ./
USER node
EXPOSE 3000
CMD ["npm", "start"]
