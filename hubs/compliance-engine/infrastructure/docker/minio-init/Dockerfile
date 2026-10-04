FROM alpine:3.20

# minio/mc sur Docker Hub exige désormais une authentification (changement de
# licence MinIO) — on récupère le binaire mc directement depuis dl.min.io
# (même pattern que backup/Dockerfile juste à côté), pas via un registre Docker.
RUN apk add --no-cache curl ca-certificates \
    && curl -fsSL https://dl.min.io/client/mc/release/linux-amd64/mc -o /usr/local/bin/mc \
    && chmod +x /usr/local/bin/mc \
    && mc --version

ENTRYPOINT ["/bin/sh"]
