#!/bin/bash

GCLOUD_PROJECT_NAME=vocal-pathway-313217
GCLOUD_ACCESS_TOKEN=$(gcloud auth print-access-token)
TEMPLATE_VERSION=0-1-4
REPO_NAME=taclayt2
CONTAINER_NAME_PREFIX=bobjr-
IMAGE_NAME=bob-jr-lava:0.1.1

PAYLOAD=$(cat ./rest/createTemplateRequestBody_v2.json)
PAYLOAD=${PAYLOAD//"{{templateVersion}}"/${TEMPLATE_VERSION}}
PAYLOAD=${PAYLOAD//"{{repoName}}"/${REPO_NAME}}
PAYLOAD=${PAYLOAD//"{{gcloudProjectName}}"/${GCLOUD_PROJECT_NAME}}
PAYLOAD=${PAYLOAD//"{{containerNamePrefix}}"/${CONTAINER_NAME_PREFIX}}
PAYLOAD=${PAYLOAD//"{{imageName}}"/${IMAGE_NAME}}

curl --location "https://www.googleapis.com/compute/v1/projects/${GCLOUD_PROJECT_NAME}/global/instanceTemplates" \
--header "Authorization: Bearer ${GCLOUD_ACCESS_TOKEN}" \
--header "Accept: application/json" \
--header "Content-Type: application/json" \
--data-raw "${PAYLOAD}"