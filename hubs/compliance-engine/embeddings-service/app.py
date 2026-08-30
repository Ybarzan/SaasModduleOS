"""Service local d'embeddings, auto-hébergé — aucune donnée ne sort de ce
conteneur. Utilisé par le backend Java (com.incokalk.service.ml.EmbeddingsClient)
pour la recherche sémantique de codes HS (voir SemanticClassificationService)."""

import logging

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("embeddings-service")

MODEL_NAME = "paraphrase-multilingual-MiniLM-L12-v2"

app = FastAPI(title="Praxio Embeddings Service")
model = SentenceTransformer(MODEL_NAME)

# La toute premiere inference apres le chargement du modele a une latence
# nettement plus elevee que les suivantes (warm-up PyTorch/threads) -- sans
# ca, un appel batch reel (ex: l'ingestion NENC au demarrage du backend, ~250
# textes) peut depasser le timeout HTTP cote Java alors que le conteneur est
# deja marque "healthy" (le /health ne fait qu'une verification statique,
# sans inference). Ce warm-up absorbe ce cout une seule fois, avant que le
# service n'accepte des requetes.
model.encode(["warm-up"], normalize_embeddings=True)
logger.info("Modele charge et rechauffe: %s", MODEL_NAME)


class EncodeRequest(BaseModel):
    texts: list[str]


class EncodeResponse(BaseModel):
    embeddings: list[list[float]]
    model: str
    dimensions: int


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/v1/embeddings/encode", response_model=EncodeResponse)
def encode(request: EncodeRequest):
    vectors = model.encode(request.texts, convert_to_numpy=True, normalize_embeddings=True)
    return EncodeResponse(
        embeddings=vectors.tolist(),
        model=MODEL_NAME,
        dimensions=vectors.shape[1] if len(vectors) > 0 else model.get_sentence_embedding_dimension(),
    )
