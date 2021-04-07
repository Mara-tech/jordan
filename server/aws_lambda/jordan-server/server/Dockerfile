FROM public.ecr.aws/lambda/python:3.8

COPY app.py jordan_backend.py jordan_log.py requirements.txt logging.yml ./

RUN python3.8 -m pip install -r requirements.txt -t .

CMD ["app.app"]
