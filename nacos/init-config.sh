#!/usr/bin/env bash
# 将 nacos/config 下的配置发布到 Nacos 配置中心。
# 用法：./nacos/init-config.sh [nacos地址]，默认 127.0.0.1:8848
set -euo pipefail

NACOS_ADDR="${1:-${NACOS_SERVER_ADDR:-127.0.0.1:8848}}"
GROUP="${NACOS_GROUP:-config}"
NAMESPACE="${NACOS_NAMESPACE:-myShop}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Windows 下的 curl 是原生程序，不认 /e/... 形式的 MSYS 路径，需要转换
to_native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        echo "$1"
    fi
}

for file in "${SCRIPT_DIR}"/config/*.yml; do
    data_id="$(basename "${file}")"
    native_file="$(to_native_path "${file}")"
    # 使用 content@文件 的方式上传，curl 会按原始字节读取，保留 UTF-8 编码
    response="$(curl -sS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${GROUP}" \
        --data-urlencode "tenant=${NAMESPACE}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${native_file}")"
    echo "publish ${data_id}: ${response}"
done
