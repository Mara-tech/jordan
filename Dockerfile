# Image of the local development stack (docker-compose.yml). Production is
# server/Dockerfile, which runs gunicorn; this one runs the Flask development
# server, so that JORDAN_DEBUG keeps a meaning here.
FROM python:3.11-slim

WORKDIR /app

COPY server/requirements.txt server/requirements.txt
RUN pip install --no-cache-dir -r server/requirements.txt

COPY server/ server/

# The server's modules import one another as top-level names (`import api`), so
# they only resolve with server/ as the working directory — the same as
# `cd server && python jordan_server.py` outside a container. Started as
# `python -m server.jordan_server` from /app, it died on
# `ModuleNotFoundError: No module named 'api'` (JRD-20).
WORKDIR /app/server

ENV PYTHONUNBUFFERED=1

EXPOSE 5000

CMD ["python", "jordan_server.py"]
