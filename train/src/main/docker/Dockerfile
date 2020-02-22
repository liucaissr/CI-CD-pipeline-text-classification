FROM python:3.7.6

WORKDIR /app

# Install dependencies with pip and copy script
COPY train/src/main/docker/requirements.txt ./

EXPOSE 8080

VOLUME /app

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r requirements.txt

COPY train/src/main/docker/configDocker.sh ./

RUN chmod +x configDocker.sh

CMD ["./configDocker.sh"]