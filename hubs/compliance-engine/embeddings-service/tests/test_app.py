"""Tests du service d'embeddings — voir conftest.py pour le double de test
qui remplace sentence-transformers (pas de téléchargement de modèle ici)."""
from fastapi.testclient import TestClient

import app as app_module

client = TestClient(app_module.app)


def test_health_returns_ok_and_model_name():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "model": app_module.MODEL_NAME}


def test_encode_returns_one_embedding_per_text():
    response = client.post("/v1/embeddings/encode", json={"texts": ["chaussures", "shoes"]})

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == app_module.MODEL_NAME
    assert body["dimensions"] == 4
    assert len(body["embeddings"]) == 2
    assert all(len(vec) == 4 for vec in body["embeddings"])


def test_encode_single_text():
    response = client.post("/v1/embeddings/encode", json={"texts": ["code HS 8517.12"]})

    assert response.status_code == 200
    assert len(response.json()["embeddings"]) == 1


def test_encode_empty_texts_falls_back_to_model_dimension():
    # Couvre la branche ternaire de app.py : quand vectors est vide, dimensions
    # vient de model.get_sentence_embedding_dimension() plutôt que vectors.shape[1].
    response = client.post("/v1/embeddings/encode", json={"texts": []})

    assert response.status_code == 200
    body = response.json()
    assert body["embeddings"] == []
    assert body["dimensions"] == 4


def test_encode_missing_texts_field_returns_422():
    response = client.post("/v1/embeddings/encode", json={})

    assert response.status_code == 422


def test_encode_texts_wrong_type_returns_422():
    response = client.post("/v1/embeddings/encode", json={"texts": "pas une liste"})

    assert response.status_code == 422


def test_encode_non_string_items_returns_422():
    response = client.post("/v1/embeddings/encode", json={"texts": [1, 2, 3]})

    assert response.status_code == 422
