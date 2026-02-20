FROM node:24-alpine AS build
WORKDIR /workspace/spellcards-web

RUN apk add --no-cache openjdk17-jre-headless

COPY spellcards-web/package*.json ./
RUN npm ci

COPY spellcards-web .
COPY spellcards-api/openapi ../spellcards-api/openapi

RUN npm run generate:client && npm run build

FROM nginx:1.29-alpine
COPY spellcards-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/spellcards-web/dist /usr/share/nginx/html

EXPOSE 80
