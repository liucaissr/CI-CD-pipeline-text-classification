# Caggle - Question Classifier CI/CD pipeline 
The projects in this repository implemented [Kaggle-like](https://www.kaggle.com/)  a CI/CD pipeline of data science process in the context of NLP. It mainly realized the following features:

| Feature | description | 
| :--- |:-------------| 
| 1    | collect data from data warehouse into hdfs using spark jobs in scala (``folder:``[``spark-dataset``](../spark-dataset/src/main))| 
| 2    | enable collaborative team members to train ML model within jupyter notebook using Docker or local machine in the same context (``folder:``[``train``](../train)) |  
| 3    | build and integrate model automatically to production for real-time prediction with tool sbt and jenkins |  

Meanwhile, it conquers some challenges encountered frequently in a data science process:
1. version control of jupyter notebooks and export its running result
1. dependency management among collaboration team
1. version control of data

### Quick demo of feature 2 in docker
1. build docker image and run the container
    ```bash
    ./train/runDocker.sh Dev 1-SNAPSHOT initImage
    ```
1. run jupyter notebook and open the provided link in browser
    ```bash
    jupyter notebook --ip=0.0.0.0 --port=8080 --no-browser --allow-root
    ```
1. use git to track changes in jupyter notebook, two git hooks in hooks folder will handle the following tasks automatically:
    - git add file.ipynb: the output of cells in notebooks will be cleaned
    - git commit: check changes in package dependencies, commit it when needed.
    - git push: convert the changed notebooks into html and commit it. 
    ( [notebook result](https://htmlpreview.github.io/?https://github.com/liucaissr/demo_Caggle_html/blob/master/dummy_docker.html) )

1. after built the image, to restart the contianer:
   ```bash
    ./train/runDocker.sh Dev 1-SNAPSHOT
    ```
   to reinitialize the contianer:
   ```bash
    ./train/runDocker.sh Dev 1-SNAPSHOT initContainer
    ```

To better demonstrate the usability of this pipeline, two workflow (use cases) were designed and implemented:

#### workflow 1 for development:
1. Schedule the spark job for collecting data on daily basis.
1. train model with local machine or dev-docker
1. use git to track all changes with the help of git hooks (jupyter notebooks, dependencies and models)


#### workflow 2 for deployment:
1. Schedule the spark job for collecting data on daily basis
1. trigger a jenkins pipeline to automatically deploy a new model into production
1. jenkins pipeline includes: 
    1. collect latest data by triggering spark-job
    1. train model within dep-docker with versioned jupyter notebook and dependencies
    1. generate report from notebook with nbconvert
    1. deploy trained model into micro-service for real-time prediction
 
    
## Data Acquisition and Understanding (project spark-dataset)

- It exports related dataset to hdfs
- necessary statistic of the data (such as rows, size) is written into sql database 'lugger' experimental/ai_dataset_exp for sanity check

1. compile spark-job:
    ```bash
    sbt sparkDataset/sparkSubmit
    ```
1. start positive job: 
    ```bash
    spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Positive spark-dataset-assembly-1-SNAPSHOT.jar
    ```
1. start negative job: 
    ```bash
    spark2-submit --conf spark.ui.port=4051  --driver-class-path /etc/hadoop/conf --class jobs.Dwh2Negative spark-dataset-assembly-1-SNAPSHOT.jar
    ```

## Modeling (project train)

In this stage, developers are able to do ML modeling using jupyter notebooks on Docker or on local machine.


### Run notebook locally with pyenv

```
pyenv install 3.6.9
pyenv virtualenv 3.6.9 qc-contactrequest
pyenv activate qc-contactrequest
pip install --upgrade pip && pip install --no-cache-dir -r train/src/main/docker/requirements.txt
```

### Run notebook locally with dev-docker

start dev-docker with shell script
```bash
./train/runDocker.sh Dev 1-SNAPSHOT
```
or sbt command 
```bash
sbt initDocker
sbt devDocker
```

Clean up docker when needed:
```bash
./train/runDocker.sh init 1-SNAPSHOT
```

## Source:
TDSP Piepeline instruction:
https://docs.microsoft.com/en-us/azure/machine-learning/team-data-science-process/overview
- Business Understanding
- Business Understanding
- Data Acquisition and Understanding
- Modeling
- Deployment
- Customer Acceptance    

