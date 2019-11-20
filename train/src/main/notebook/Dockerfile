FROM python:3.6.9

WORKDIR /usr/src/app

# Install dependencies with pip
COPY notebook ./

RUN pip install --upgrade pip && \
	pip install --no-cache-dir -r requirements.txt

#CMD ["sh","jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root"]
# CMD ["sh","-c","jupyter nbconvert --to notebook --execute hallo_gfaggle_hdfs.ipynb && jupyter nbconvert hallo_gfaggle_hdfs.nbconvert.ipynb --output-dir='results' --output='commit1.nbconvert.html'"]

