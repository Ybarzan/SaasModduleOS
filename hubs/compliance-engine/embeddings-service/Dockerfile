FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
# --extra-index-url pointe pip vers les roues CPU-only de PyTorch (pas de CUDA,
# image bien plus légère) ; les autres paquets viennent normalement de PyPI.
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu \
    -r requirements.txt

# Télécharge et met en cache le modèle pendant le build — le conteneur n'a
# besoin d'aucun accès réseau au runtime (aucune donnée ne sort une fois construit).
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')"

COPY app.py .

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
