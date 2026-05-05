#!/bin/bash
set -e

# Docker socket: on Docker Desktop the socket is owned by root:root (GID 0)
if [ -S /var/run/docker.sock ]; then
    chmod 666 /var/run/docker.sock
fi

exec gosu jenkins /usr/bin/tini -- /usr/local/bin/jenkins.sh "$@"
