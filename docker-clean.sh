#!/bin/sh

set -x

# Remove the docker volume that stores cached build artifacts.
# This also stops and removes any container using the volume.
echo 'Clearing build cache: '
docker volume remove -f pleo-antaeus-build-cache

echo 'Cleaning docker images'
# Remove all pleo-antaeus images.
docker images --quiet --filter="reference=pleo-antaeus:*" | \
 while read image; do
   docker rmi -f "$image"
 done

# Remove all cron images.
docker stop $(docker ps --filter=network=antaeus-network --quiet)
docker images --quiet --filter="reference=cron:*" | \
 while read image; do
   docker rmi -f "$image"
 done

docker network rm antaeus-network

# Optionally reclaim space of dangling images.
echo 'Run "docker system prune" to clear disk space?'
docker system prune
