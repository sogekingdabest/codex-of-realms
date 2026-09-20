#!/bin/sh
set -eu
# Private disposable catalogue; both original weight stores remain read-only.
mkdir -p /evaluation-models/blobs /evaluation-models/manifests
for directory in /existing-ollama/models /native-models; do
    if [ -d "$directory/manifests" ]; then
        cp -R "$directory/manifests/." /evaluation-models/manifests/
    fi
    for blob in "$directory"/blobs/*; do
        [ -f "$blob" ] || continue
        name=${blob##*/}
        [ -e "/evaluation-models/blobs/$name" ] || ln -s "$blob" "/evaluation-models/blobs/$name"
    done
done
exec /bin/ollama serve
