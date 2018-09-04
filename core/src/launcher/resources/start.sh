#!/bin/sh
# This script starts Oversigt and remembers its process ID.

cd=$(dirname "$0")
cd=$(cd "$cd" && pwd)

"${cd}/stop.sh"

# Start dashboard
echo "Starting"
pushd "${cd}" > /dev/null
/usr/bin/java "-Duser.dir=${cd}" -jar *-launcher.jar 2>&1 > /dev/null &
popd > /dev/null

# Save process ID
echo "$!" > "${cd}/dashboard.pid"
