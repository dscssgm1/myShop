#!/usr/bin/env bash
# 将 nacos/config 下的配置发布到 Nacos 配置中心。
# 用法：./nacos/init-config.sh [nacos地址]，默认 127.0.0.1:8848
set -euo pipefail

NACOS_ADDR="${1:-${NACOS_SERVER_ADDR:-127.0.0.1:8848}}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for file in "${SCRIPT_DIR}"/config/*.yml; do
    data_id="$(basename "${file}")"
    response="$(curl -sS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${GROUP}" \
        --data-urlencode "tenant=${NAMESPACE}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${file}")"
    echo "publish ${data_id}: ${response}"
done
