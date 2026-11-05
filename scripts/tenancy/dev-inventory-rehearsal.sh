#!/bin/bash
set +x
set -euo pipefail
umask 077
cd /tmp
mkdir -m 700 dev-inventory
cd dev-inventory
echo '8d24940ebe3675959b518fd41ea16b4e2182d6a75c09777db09cfa64e18834a1  /opt/inventory-mongosh.tgz' | sha256sum -c --status
tar -xzf /opt/inventory-mongosh.tgz -C /opt
cat >/etc/mongosh.conf <<'CONFIG'
mongosh:
  disableLogging: true
  disableSchemaSampling: true
  forceDisableTelemetry: true
  historyLength: 0
  updateURL: ""
  snippetAutoload: false
  browser: false
CONFIG
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=fixture-ca -keyout ca.key -out ca.crt 2>/dev/null
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=wrong-ca -keyout wrong.key -out wrong.crt 2>/dev/null
openssl req -newkey rsa:2048 -nodes -subj /CN=localhost -keyout server.key -out server.csr 2>/dev/null
printf 'subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth,clientAuth\n' > server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 1 -extfile server.ext -out server.crt 2>/dev/null
cat server.crt server.key > server.pem
head -c 756 /dev/urandom | base64 -w 0 >replica.key
mongod --fork --logpath /tmp/dev-inventory/mongod.log --dbpath /data/db --bind_ip 127.0.0.1 --port 27033 \
  --replSet repsdevinventory --keyFile /tmp/dev-inventory/replica.key --tlsMode requireTLS \
  --tlsCertificateKeyFile /tmp/dev-inventory/server.pem --tlsCAFile /tmp/dev-inventory/ca.crt \
  --tlsAllowConnectionsWithoutCertificates >/dev/null
shell=(/opt/mongosh-2.14.1-linux-x64/bin/mongosh --norc --quiet --nodb)
export REPS_DEV_INVENTORY_SYNTHETIC=approved-disposable-fixture
trap 'unset REPS_DEV_INVENTORY_PASSWORD REPS_DEV_INVENTORY_ADMIN_PASSWORD' EXIT
REPS_DEV_INVENTORY_PASSWORD="$(openssl rand -hex 32)"
REPS_DEV_INVENTORY_ADMIN_PASSWORD="$(openssl rand -hex 32)"
export REPS_DEV_INVENTORY_PASSWORD REPS_DEV_INVENTORY_ADMIN_PASSWORD
fixture() { "${shell[@]}" --file /tools/dev-inventory-rehearsal.mongosh.js; }
export FIXTURE_STAGE=init
fixture
export FIXTURE_STAGE=ready
for i in $(seq 1 30); do
  if fixture >/dev/null 2>&1; then break; fi
  sleep 1
done
export FIXTURE_STAGE=seed
fixture
run_inventory() (
  unset REPS_DEV_INVENTORY_ADMIN_PASSWORD
  "${shell[@]}" --file /tools/dev-inventory.mongosh.js
)
refuse() {
  local expected="$1" result
  if result=$(run_inventory); then exit 1; fi
  test "$result" = "Inventory refused or failed during $expected."
  test ! -e report.json
}
mv ca.crt trusted.crt
cp wrong.crt ca.crt
refuse 'TLS and topology'
mv trusted.crt ca.crt
export FIXTURE_STAGE=wrong-hostname
fixture
REPS_DEV_INVENTORY_PASSWORD=incorrect refuse authentication
export FIXTURE_STAGE=extra
fixture
refuse 'effective privileges'
export FIXTURE_STAGE=restrict
fixture
run_inventory
test "$(stat -c '%a' report.json)" = 600
before_report="$(sha256sum report.json)"
if result=$(run_inventory); then exit 1; fi
test "$result" = 'Inventory refused or failed during aggregate publication.'
test "$before_report" = "$(sha256sum report.json)"
test -z "$(find . -maxdepth 1 -name '.inventory-*' -print)"
export FIXTURE_STAGE=verify
fixture
echo 'Authenticated TLS inventory rehearsal passed.'
