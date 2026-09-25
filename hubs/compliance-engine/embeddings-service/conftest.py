"""Empêche les tests d'importer le vrai sentence-transformers/torch : ces tests
unitaires vérifient la logique de app.py (endpoints, validation, formes de
réponse), pas le modèle ML lui-même — pas besoin de télécharger ~500 Mo depuis
huggingface.co ni d'installer torch pour les faire tourner. La validation avec
le vrai modèle se fait au build de l'image Docker (voir job CI "docker-embeddings").
"""
import sys
import types


class _FakeVectors:
    """Imite juste assez l'API numpy utilisée par app.py (shape, len, tolist)."""

    def __init__(self, rows):
        self._rows = rows
        self.shape = (len(rows), len(rows[0]) if rows else 0)

    def __len__(self):
        return len(self._rows)

    def tolist(self):
        return self._rows


class FakeSentenceTransformer:
    DIMENSIONS = 4

    def __init__(self, *args, **kwargs):
        pass

    def encode(self, texts, convert_to_numpy=False, normalize_embeddings=False):
        rows = [[0.1, 0.2, 0.3, 0.4] for _ in texts]
        return _FakeVectors(rows) if convert_to_numpy else rows

    def get_sentence_embedding_dimension(self):
        return self.DIMENSIONS


if "sentence_transformers" not in sys.modules:
    fake_module = types.ModuleType("sentence_transformers")
    fake_module.SentenceTransformer = FakeSentenceTransformer
    sys.modules["sentence_transformers"] = fake_module
