# syntax=docker/dockerfile:1.7

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
# MinIO a ~150 dépendances Go directes -- sur un réseau instable, un paquet
# individuel qui timeout côté proxy.golang.org (observé : "TLS handshake
# timeout") fait échouer tout le build, qui redémarre alors de zéro sans le
# cache mount ci-dessous. Le cache persiste les modules déjà téléchargés
# entre deux tentatives (un rebuild après coupure ne retélécharge que ce qui
# manquait) et la boucle retente 3 fois avant d'abandonner.
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    sh -c 'n=0; until CGO_ENABLED=0 go build -tags kqueue -trimpath -o /out/minio .; do \
      n=$((n+1)); [ "$n" -ge 3 ] && exit 1; echo "go build a échoué (tentative $n/3), nouvelle tentative..."; sleep 5; \
    done'

FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl
COPY --from=build /out/minio /usr/bin/minio

EXPOSE 9000 9001
VOLUME /data
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
