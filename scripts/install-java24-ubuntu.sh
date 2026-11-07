#!/usr/bin/env bash
set -Eeuo pipefail

# Optional host JDK 24 installer for Ubuntu/Debian.
# The Dockerized Family Album does NOT require Java installed on the VPS host.
if [[ "${EUID}" -ne 0 ]]; then
  echo "Run with sudo: sudo bash $0" >&2
  exit 1
fi
command -v apt-get >/dev/null 2>&1 || {
  echo "This script supports Ubuntu/Debian (apt-get) only." >&2
  exit 1
}

case "$(uname -m)" in
  x86_64|amd64) ARCH="x64" ;;
  aarch64|arm64) ARCH="aarch64" ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

apt-get update
apt-get install -y ca-certificates curl tar
JAVA_HOME="/opt/temurin-24"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

URL="https://api.adoptium.net/v3/binary/latest/24/ga/linux/${ARCH}/jdk/hotspot/normal/eclipse"
curl --fail --location --retry 3 "$URL" --output "$TMP_DIR/jdk24.tar.gz"
install -d "$JAVA_HOME"
tar -xzf "$TMP_DIR/jdk24.tar.gz" --strip-components=1 -C "$JAVA_HOME"

update-alternatives --install /usr/bin/java java "$JAVA_HOME/bin/java" 2400
update-alternatives --set java "$JAVA_HOME/bin/java"
if [[ -x "$JAVA_HOME/bin/javac" ]]; then
  update-alternatives --install /usr/bin/javac javac "$JAVA_HOME/bin/javac" 2400
  update-alternatives --set javac "$JAVA_HOME/bin/javac"
fi

cat >/etc/profile.d/java24.sh <<EOF
export JAVA_HOME=$JAVA_HOME
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
chmod 644 /etc/profile.d/java24.sh
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
echo "JAVA_HOME=$JAVA_HOME"
