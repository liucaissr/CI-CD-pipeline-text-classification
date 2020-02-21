FROM python:3.6.9

WORKDIR /app

# Install dependencies with pip and copy script
COPY train/src/main/docker/tmp.txt ./

EXPOSE 8080

VOLUME /app

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r tmp.txt

COPY train/src/main/docker/configDocker.sh ./

RUN chmod +x configDocker.sh

CMD ["./configDocker.sh"]