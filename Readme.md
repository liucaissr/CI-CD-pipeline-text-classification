# Question Classifier of deletion reason contact request
TDSP Piepeline instruction:
https://docs.microsoft.com/en-us/azure/machine-learning/team-data-science-process/overview
- Business Understanding
- Data Acquisition and Understanding
- Modeling
- Deployment
- Customer Acceptance
    
## Data Acquisition and Understanding

### Project spark-dataset
- It exports dataset related to contactrequest questions to hdfs directory:
http://hue.endor.gutefrage.net/hue/filebrowser/view%3D/user/hue/#/data-projects/dataset/ivy-repo/releases/net.gutefrage.data.ml/qc-deletionreason-contactrequest-ds
- start positive job: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Positive spark-dataset-assembly-1-SNAPSHOT.jar```
- start negative job: ```spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Negative spark-dataset-assembly-1-SNAPSHOT.jar```
- Statistic is written in to lugger experimental/ai_dataset_exp temporarily

## Run notebook locally with pyenv

```
pyenv virtualenv 3.6.9 qc-contactrequest
pyenv activate qc-contactrequest
pip install --upgrade pip && pip install --no-cache-dir -r requirements.txt
```


## Run notebook locally with docker

### build image 
```
docker build -t qc-contactrequest:1-snapshot .
```

### run docker nb convert
```
docker run --name qc-contactrequest-container -v $PWD/../../../target/notebook:/usr/target/notebook -v $PWD/../notebook:/usr/src/main/notebook -p 8080:8080 -it qc-contactrequest:1-snapshot  jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=-1 qc-contact-request-deletionreason.ipynb && jupyter nbconvert qc-contact-request-deletionreason.ipynb --output-dir='output' --output='commit1.nbconvert.html'
```

### run jupyter notebook

Data found at target/notebook/input/

```
docker run --name qc-contactrequest-container -v $PWD/../../../target/notebook:/usr/target/notebook -v $PWD/../notebook:/usr/src/main/notebook -p 8080:8080 -it qc-contactrequest:1-snapshot jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
```



### run bash inside the docker image to test commands with data bind and port
```
 docker run --name qc-contactrequest-container -v $PWD/../../../target/notebook:/usr/src/main/notebook -v $PWD/../notebook:/usr/src/main/notebook -p 8080:8080 -it qc-contactrequest:1-snapshot bash
```

inside the image: 
    - run jupyter notebeook
    ```
    jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
    ```
    - run nbconvert 
    ```
    jupyter nbconvert --to notebook --execute --ExecutePreprocessor.timeout=-1 qc-contact-request-deletionreason.ipynb
    ```
    
### clean docker

remove image:
```
docker rmi -f qc-contactrequest
```
remove container:
``` 
docker rm -f qc-contactrequest-container
```
    

## Old stuff
    
docker run jupyter notebook from outside of docker:


run jupyter notebook manually with data bind and port for jupyter notebood:
```
docker run --name qc-contactrequest-container -v /Users/agnes.ferenczi/Projects/qc-contactrequest/train/src/main/notebook/jupyter:/usr/src/app/jupyter/ -p 8080:8080 -it qc-contactrequest:latest  jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
```


Now copy the address and paste it into a browser. Run the commands. 
Changes in the notebook and outputs now appear in git, even if they were changed or created in the docker.


run nbconvert: 
try
```

docker run --name qc-contactrequest-container -v /Users/agnes.ferenczi/Projects/qc-contactrequest/train/src/main/notebook/jupyter:/usr/src/app/jupyter/  -it qc-contactrequest:latest jupyter nbconvert --to notebook --execute hallo_gfaggle_hdfs.ipynb && jupyter nbconvert hallo_gfaggle_hdfs.nbconvert.ipynb --output-dir='results' --output='commit1.nbconvert.html
```





run bash in image (optional): 
```
docker run -it qc-contactrequest:latest bash
```

run bash in image (optional): 
```
docker run --name qc-contactrequest-container -v /Users/agnes.ferenczi/Projects/qc-contactrequest/train/src/main/notebook/jupyter:/usr/src/app/jupyter/ -it qc-contactrequest:latest bash
```

Once inside the image with bash, try to run nbconvert:
 
```
jupyter nbconvert --to notebook --execute qc-contact-request-deletionreason.ipynb
```
 
 

run bash in image with data bind:
```
docker run --name qc-contactrequest-container -v /Users/agnes.ferenczi/Projects/qc-contactrequest/train/src/main/data:/usr/src/main/data -it qc-contactrequest:latest bash
```


run jupyter notebook manually in docker image (not needed): 
```
docker run --name qc-contactrequest-container -p 8080:8080 -it qc-contactrequest:latest  jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
```


Notes:
- If the script is copied over to the docker image in the Dockerfile with the COPY command, then it can not be edited. 
- If we want to just run the script, then it should not be edited, so this is fine
- If we want to develop locally, then the script can not be copied to docker and ran there
 - Remove big folders from inside the tree of folders where Dockerfile sits. 
 Otherwise everything is sent to build context docker deamon. If this is too big, the deamon crashes.
  
