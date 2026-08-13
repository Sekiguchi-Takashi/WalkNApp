#!/bin/bash
cd "$(dirname "$0")"
TOKEN=$(git config --global github.token)
GHUSER=Sekiguchi-Takashi
REPO=WalkNApp
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: token $TOKEN" https://api.github.com/user/repos -d "{\"name\":\"$REPO\",\"private\":true}"
if [ ! -d .git ]; then git init -b main; fi
git remote remove origin 2>/dev/null
git remote add origin https://$GHUSER:$TOKEN@github.com/$GHUSER/$REPO.git
git add -A
git commit -m "${1:-update}"
git push -u origin main
