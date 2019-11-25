FROM python:3.6.9

WORKDIR /usr/src/app

# Install dependencies with pip
COPY train/src/main/notebook/requirements.txt ./

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r requirements.txt

CMD ["sh","-c","jupyter nbconvert --to notebook --execute dummy_docker.ipynb && jupyter nbconvert dummy_docker.nbconvert.ipynb --output-dir='results' --output='commit1.nbconvert.html'"]

