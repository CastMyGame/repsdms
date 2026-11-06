#!/bin/bash
set +x
set -euo pipefail
umask 077
mkdir -m 700 /tmp/atlas-fixture /tmp/atlas-output
cd /tmp/atlas-fixture
echo '8d24940ebe3675959b518fd41ea16b4e2182d6a75c09777db09cfa64e18834a1  /opt/inventory-mongosh.tgz' | sha256sum -c --status
tar -xzf /opt/inventory-mongosh.tgz -C /opt
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=fixture-ca -keyout ca.key -out ca.crt 2>/dev/null
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=wrong-ca -keyout wrong.key -out wrong.crt 2>/dev/null
openssl req -newkey rsa:2048 -nodes -subj /CN=fixture-server -keyout server.key -out server.csr 2>/dev/null
printf 'subjectAltName=DNS:ac-fykprpn-shard-00-00.xgrmfbb.mongodb.net,DNS:ac-fykprpn-shard-00-01.xgrmfbb.mongodb.net,DNS:ac-fykprpn-shard-00-02.xgrmfbb.mongodb.net\nextendedKeyUsage=serverAuth,clientAuth\n' >server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 1 -extfile server.ext -out server.crt 2>/dev/null
cat server.crt server.key >server.pem
head -c 756 /dev/urandom | base64 -w 0 >replica.key
for n in 0 1 2; do
  ip="127.0.0.$((n+2))"
  echo "$ip ac-fykprpn-shard-00-0$n.xgrmfbb.mongodb.net" >>/etc/hosts
  mkdir "/tmp/atlas-fixture/db$n"
  mongod --fork --logpath "/tmp/atlas-fixture/mongo$n.log" --dbpath "/tmp/atlas-fixture/db$n" \
    --bind_ip "$ip" --port 27017 --replSet atlas-bafhxz-shard-0 --keyFile /tmp/atlas-fixture/replica.key \
    --tlsMode requireTLS --tlsCertificateKeyFile /tmp/atlas-fixture/server.pem \
    --tlsCAFile /tmp/atlas-fixture/ca.crt --tlsAllowConnectionsWithoutCertificates >/dev/null
done
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
trap 'unset REPS_ATLAS_FIXTURE_READER REPS_ATLAS_FIXTURE_ADMIN' EXIT
REPS_ATLAS_FIXTURE_READER="$(openssl rand -hex 32)"
REPS_ATLAS_FIXTURE_ADMIN="$(openssl rand -hex 32)"
export REPS_ATLAS_FIXTURE_READER REPS_ATLAS_FIXTURE_ADMIN
/opt/mongosh-2.14.1-linux-x64/bin/mongosh --norc --quiet --nodb --file /tools/atlas-inventory-rehearsal.mongosh.js
