# Introduction

The purpose of this project is to create a data set that can be reliably used to tune a Kafka cluster.

## Stack Exchange dataset(s)

The [Stack Exchange](https://archive.org/download/stackexchange) dataset(s) are a complete dump of 
the data that for sites like Stack Overflow. This dataset is large and each row can be significantly 
different than the previous row. This is great for testing different settings like compression. 

## Generating data sets

Download data from the [here](https://archive.org/download/stackexchange).

```bash
java -jar stackoverflow-datagenerator-0.0.1-SNAPSHOT.jar --input stackoverflow.com-Votes.7z --output data/
```

This will read from the 7z file that was downloaded and generate avro data files that will be used for 
testing in the output directory. 

## Load testing

```bash
--producer-config foo.properties --create-topic 1.avro 2.avro 3.avro
```