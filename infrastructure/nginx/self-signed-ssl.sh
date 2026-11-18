#!/bin/bash
# =============================================================================
# Generate Self-Signed TLS Certificates for VideoToText AI Production Testing
# =============================================================================

set -e

CERT_DIR="$(dirname "$0")/ssl"
mkdir -p "$CERT_DIR"

echo "Generating self-signed SSL/TLS certificates in $CERT_DIR..."

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$CERT_DIR/tls.key" \
  -out "$CERT_DIR/tls.crt" \
  -subj "/C=US/ST=California/L=SanFrancisco/O=VideoToTextAI/OU=Engineering/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:*.videototext.ai,IP:127.0.0.1"

chmod 600 "$CERT_DIR/tls.key"
chmod 644 "$CERT_DIR/tls.crt"

echo "Certificates successfully generated:"
echo "  Private Key: $CERT_DIR/tls.key"
echo "  Certificate: $CERT_DIR/tls.crt"
