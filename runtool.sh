#!/bin/sh
set -x

# Check runtime dependencies
if [ ! -d /etc/frr  -a ! -e /bin/vtysh ]; then
	echo "Frrouting daemon seems to be not installed. Exiting"
	exit 1
fi

if [ ! -e /etc/frr/ospfd.conf ]; then
	echo "Copying the ospfd configuration for this project to be run."
	echo "Requires root permissions"
	sudo cp ospfd.conf /etc/frr/
	echo "Restart services"
	sudo service frr restart || sudo systemctl restart frr.service || { echo "Error restarting services"; exit 1; }
	if [  $? == 1 ]; then
		exit 1
	fi
fi

INTERFACE="tap0"
if [ -h /sys/class/net/${INTERFACE} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE}"
	sudo dhclient tap0 || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? == 1 ]; then
		exit 1
	fi
else
	echo "tap0 hasn't been found in this computer. Exiting"
	exit 1 
fi

LIBDIR=${LIBDIR:-../lib}
JAVACP=${LIBDIR}/bcprov-jdk15on-159.jar:
JAVACP=${JAVACP}${LIBDIR}/slf4j-api-1.7.9.jar:
JAVACP=${JAVACP}${LIBDIR}/eddsa-0.2.0.jar:
JAVACP=${JAVACP}${LIBDIR}/slf4j-jdk14-1.7.9.jar:
JAVACP=${JAVACP}${LIBDIR}/jzlib-1.1.3.jar:
JAVACP=${JAVACP}${LIBDIR}/sshj-0.23.0.jar:
JAVACP=${JAVACP}.

cd bin/
java -cp ${JAVACP} Tool