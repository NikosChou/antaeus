#!/bin/sh

set -x

docker network create antaeus-network

# Create a new image version with latest code changes.
docker build . --tag pleo-antaeus
# Create a new image version with cron job.
docker build -f DockerfileCron . --tag cron

docker run -d --network antaeus-network cron

# Build the code.
docker run \
  --publish 8080:7000 \
  --rm \
  --interactive \
  --network antaeus-network \
  --tty \
  --volume pleo-antaeus-build-cache:/root/.gradle \
  --name pleo-antaeus \
  pleo-antaeus
