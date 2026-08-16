# Image of the Jordan server, built from the server/ directory as context —
# which is why server/.dockerignore, and not the one at the repository root, is
# what keeps server/.env out of the layers.
FROM python:3.11-slim

WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy server code
COPY . .

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8080

# Run as a user that owns nothing: the code and the interpreter stay owned by
# root and world-readable, so the process can read what it serves and rewrite
# neither. A flaw in the server then buys a shell that cannot patch the image it
# runs from. No home directory and no password: this account is never logged in.
# useradd rather than adduser: it ships in the passwd package, which every
# Debian image has, while adduser is a convenience that a slimmer base may drop.
RUN useradd --system --user-group --no-create-home jordan
USER jordan

# Expose port
EXPOSE 8080

# gunicorn, not `python jordan_server.py`: the Flask development server is
# single-threaded and was never written to face a network. `exec` hands PID 1 to
# gunicorn so it receives the SIGTERM of a redeploy and drains instead of being
# killed; the shell is only there to expand $PORT, which the platform sets.
# JORDAN_DEBUG has no meaning here — there is no development server to put a
# debugger in.
CMD ["sh", "-c", "exec gunicorn api:app --bind 0.0.0.0:${PORT:-8080} --workers 2 --access-logfile - --error-logfile -"]
