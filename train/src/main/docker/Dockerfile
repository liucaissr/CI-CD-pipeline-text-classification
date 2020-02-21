FROM python:3.6.9

WORKDIR /app

# Install dependencies with pip and copy script
COPY train/src/main/docker/tmp.txt ./

EXPOSE 8080

VOLUME /app

RUN git config --global core.hooksPath hooks && \
    pip install --upgrade pip && \
	pip install --no-cache-dir -r tmp.txt

