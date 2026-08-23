FROM postgres:16-alpine

# Installer le client MinIO (mc) pour l'upload des backups vers MinIO
RUN apk add --no-cache curl ca-certificates \
    && curl -fsSL https://dl.min.io/client/mc/release/linux-amd64/mc -o /usr/local/bin/mc \
    && chmod +x /usr/local/bin/mc \
    && mc --version

ENTRYPOINT ["/bin/sh"]
