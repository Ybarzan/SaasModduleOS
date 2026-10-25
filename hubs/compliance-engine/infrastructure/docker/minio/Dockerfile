# MinIO a cessé de publier des images officielles : Docker Hub a supprimé
# minio/minio, quay.io/minio/minio exige désormais une authentification
# (401), et le dépôt GitHub a été archivé (lecture seule) le 25/04/2026.
# On compile donc le serveur depuis les sources officielles, à la dernière
# version open source publiée avant l'archivage (RELEASE.2025-10-15T17-29-55Z,
# qui inclut les derniers correctifs de sécurité). Build vérifié manuellement :
# compilation OK, démarrage OK, /minio/health/live et /minio/health/ready -> 200.
FROM golang:1.24-alpine AS build

RUN apk add --no-cache git
WORKDIR /src
RUN git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z https://github.com/minio/minio.git .
RUN CGO_ENABLED=0 go build -tags kqueue -trimpath -o /out/minio .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl
COPY --from=build /out/minio /usr/bin/minio

EXPOSE 9000 9001
VOLUME /data
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
