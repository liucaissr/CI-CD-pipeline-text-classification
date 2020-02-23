FROM python:3.7.6

WORKDIR /app

# Install dependencies with pip and copy script
COPY train/src/main/docker/tmp.txt ./

EXPOSE 8080

VOLUME /app/caggle

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r tmp.txt

COPY train/src/main/docker/configDocker.sh /app

RUN chmod +x configDocker.sh

CMD ["./configDocker.sh"]