FROM python:3.11-slim

WORKDIR /app

COPY server/requirements.txt server/requirements.txt
RUN pip install --no-cache-dir -r server/requirements.txt

COPY server/ server/

EXPOSE 5000

CMD ["python", "-m", "server.jordan_server"]
