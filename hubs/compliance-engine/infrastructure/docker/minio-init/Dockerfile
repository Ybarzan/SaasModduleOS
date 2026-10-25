FROM alpine:3.20

# MinIO a interrompu la distribution open-source de mc (Docker Hub a supprimé
# minio/mc, quay.io/minio/mc exige une authentification, dl.min.io renvoie
# 410 Gone) -- on récupère donc le dernier binaire publié en open source
# (RELEASE.2025-08-13T08-35-41Z) depuis les GitHub Releases du projet, avec
# vérification de somme de contrôle.
RUN apk add --no-cache curl ca-certificates \
    && curl -fsSL -o /usr/local/bin/mc \
         https://github.com/minio/mc/releases/download/RELEASE.2025-08-13T08-35-41Z/mc.linux-amd64.RELEASE.2025-08-13T08-35-41Z \
    && echo "01f866e9c5f9b87c2b09116fa5d7c06695b106242d829a8bb32990c00312e891  /usr/local/bin/mc" | sha256sum -c - \
    && chmod +x /usr/local/bin/mc \
    && mc --version

ENTRYPOINT ["/bin/sh"]
