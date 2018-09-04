#!/bin/sh
# Stops Oversigt using the existing process ID.

cd=$(dirname "$0")
cd=$(cd "$cd" && pwd)

# Cancel if not started
if [ ! -f "${cd}/dashboard.pid" ]; then
    exit 1
fi

pid=$(cat "${cd}/dashboard.pid")

# Send kill request
echo -n "Stopping"
kill ${pid} 2> /dev/null

# Wait for shutdown
while [ $? -eq 0 ]; do
        sleep 1
        echo -n "."
        ps ${pid} > /dev/null
done
echo

# Remove process ID
rm "${cd}/dashboard.pid" 2> /dev/null
