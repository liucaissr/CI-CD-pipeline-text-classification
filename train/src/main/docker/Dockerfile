FROM python:3.6.9

WORKDIR /usr/src/main/notebook

# Install dependencies with pip and copy script
COPY train/src/main/docker/requirements.txt ./

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r requirements.txt

