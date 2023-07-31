#!/bin/bash

for i in {1..1000}
do
        mvn exec:exec
        sleep 30      # sleep for 30 seconds
done


