#!/usr/bin/env bash
set -euo pipefail

case "${RUNNER_OS}/${RUNNER_ARCH}" in
  Linux/X64) asset="linux-x64"; archive_ext="tar.gz" ;;
  macOS/X64) asset="macos-x64"; archive_ext="tar.gz" ;;
  macOS/ARM64) asset="macos-arm64"; archive_ext="tar.gz" ;;
  *) echo "::error::Unsupported runner ${RUNNER_OS}/${RUNNER_ARCH}."; exit 1 ;;
esac

if [[ "${VERICORE_VERSION}" == "latest" ]]; then
  release_json="$(curl --fail --location --retry 3 --retry-delay 1 --silent --show-error \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/sonii-shivansh/Vericore/releases/latest")"
  tag="$(printf '%s' "${release_json}" | jq -r '.tag_name')"
  [[ -n "${tag}" && "${tag}" != "null" ]] || { echo "::error::Unable to resolve the latest Vericore release tag."; exit 1; }
else
  tag="${VERICORE_VERSION}"
fi

tag="${tag#v}"
base_url="https://github.com/sonii-shivansh/Vericore/releases/download/v${tag}"
archive="vericore-${tag}-${asset}.${archive_ext}"
tmp_dir="$(mktemp -d)"
cleanup() { rm -rf "${tmp_dir}"; }
trap cleanup EXIT

curl --fail --location --retry 3 --retry-delay 1 --silent --show-error \
  -o "${tmp_dir}/${archive}" "${base_url}/${archive}"
curl --fail --location --retry 3 --retry-delay 1 --silent --show-error \
  -o "${tmp_dir}/SHA256SUMS" "${base_url}/SHA256SUMS"

expected="$(awk -v file="${archive}" 'NF >= 2 && $2 == file {print $1; exit}' "${tmp_dir}/SHA256SUMS")"
[[ -n "${expected}" ]] || { echo "::error::No SHA-256 entry found for ${archive}."; exit 1; }

if command -v sha256sum >/dev/null 2>&1; then
  actual="$(sha256sum "${tmp_dir}/${archive}" | cut -d' ' -f1)"
else
  actual="$(shasum -a 256 "${tmp_dir}/${archive}" | cut -d' ' -f1)"
fi

[[ "${actual}" == "${expected}" ]] || { echo "::error::SHA-256 verification failed for ${archive}."; exit 1; }

install_root="${RUNNER_TEMP}/vericore"
rm -rf "${install_root}"
mkdir -p "${install_root}"
tar -xzf "${tmp_dir}/${archive}" -C "${install_root}"
echo "${install_root}/vericore/bin" >> "${GITHUB_PATH}"
"${install_root}/vericore/bin/vericore" --version
