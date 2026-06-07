#!/usr/bin/env bash
# Clean up a failed/partial Qualcomm Software Center (.deb) install and reinstall
# with the EULA pre-accepted (non-interactive).
#
# Usage:
#   sudo ./scripts/install-qualcomm-software-center.sh
#   sudo ./scripts/install-qualcomm-software-center.sh /path/to/QualcommSoftwareCenter*.deb
#
# After install, launch "Qualcomm Software Center" from the app menu, sign in, and
# download QAIRT. Then set qnn.sdk.dir in local.properties to that SDK root.
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

DEB="${1:-$HOME/Downloads/QualcommSoftwareCenter1.26.7.Linux-x86.deb}"
if [[ ! -f "$DEB" ]]; then
  echo "Missing .deb: $DEB" >&2
  exit 1
fi

echo "==> Removing broken/partial QualcommSoftwareCenter package state (if any)..."
dpkg --remove --force-remove-reinstreq QualcommSoftwareCenter 2>/dev/null || true
dpkg --purge --force-all QualcommSoftwareCenter 2>/dev/null || true

echo "==> Clearing debconf EULA answers (stale CANCEL trips preinst)..."
debconf-set-selections <<< "purge QualcommSoftwareCenter qualcommsoftwarecenter/question"
debconf-set-selections <<< "purge QualcommSoftwareCenter qualcommsoftwarecenter/license_agreement"

echo "==> Removing leftover install directories..."
rm -rf /opt/qcom/softwarecenter /opt/qcom/qik3
rm -rf /var/lib/qcom /var/tmp/qcom /tmp/qcom
# Keep /opt/qcom if other Qualcomm tools use it; recreate with open perms for installer.
mkdir -p /opt/qcom /var/lib/qcom /var/tmp/qcom
chmod 777 /opt/qcom /var/lib/qcom /var/tmp/qcom

echo "==> Pre-accepting Qualcomm Software Center EULA via debconf..."
debconf-set-selections <<< "qualcommsoftwarecenter qualcommsoftwarecenter/question select I ACCEPT"

echo "==> Installing $DEB ..."
export DEBIAN_FRONTEND=noninteractive
dpkg -i "$DEB"

echo ""
echo "Qualcomm Software Center installed."
echo "Next:"
echo "  1. Launch 'Qualcomm Software Center' (GUI), sign in with your Qualcomm account."
echo "  2. Download QAIRT (AI Runtime) for Linux + Android targets."
echo "  3. Add to pocket-crew/local.properties:"
echo "       qnn.sdk.dir=/path/to/qairt/<version>"
echo "  4. Run: ./scripts/sync-qnn-jniLibs.sh"
