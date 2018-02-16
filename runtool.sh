#!/bin/sh
DEBUG=
DHCPPIDFILE=dhclient.pid
PORT=8787
case $1 in
	"")
		;;
	"-d")
		DEBUG="-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,address=${PORT},server=y,suspend=y"
		;;
	*)
		echo "Usage: ./runtool.sh [ -d]"
		echo "		-d 	enable jvm options for debugging the tool"
		;;
esac 

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

INTERFACE0="tap0"
if [ -h /sys/class/net/${INTERFACE0} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE0}"
	sudo dhclient -pf ${DHCPPIDFILE} ${INTERFACE0} || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? -ne 0 ]; then
		exit 1
	fi
else
	echo "${INTERFACE0} hasn't been found in this computer. Exiting"
	exit 1 
fi


INTERFACE1="tap1"
if [ -h /sys/class/net/${INTERFACE1} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE1}"
	sudo dhclient -pf ${DHCPPIDFILE} ${INTERFACE1} || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? -ne 0 ]; then
		exit 1
	fi
else
	echo "${INTERFACE1} hasn't been found in this computer. Exiting"
	exit 1 
fi

INTERFACE2="tap2"
if [ -h /sys/class/net/${INTERFACE2} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE2}"
	sudo dhclient -pf ${DHCPPIDFILE} ${INTERFACE2} || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? -ne 0 ]; then
		exit 1
	fi
else
	echo "${INTERFACE2} hasn't been found in this computer. Exiting"
	exit 1 
fi

INTERFACE3="tap3"
if [ -h /sys/class/net/${INTERFACE3} ]; then
	echo "Waiting DHCP lease from interface ${INTERFACE3}"
	sudo dhclient -pf ${DHCPPIDFILE} ${INTERFACE3} || { echo "DHCP Error. Check connectivity"; exit 1; }
	if [ $? -ne 0 ]; then
		exit 1
	fi
else
	echo "${INTERFACE3} hasn't been found in this computer. Exiting"
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
java ${DEBUG} -cp ${JAVACP} Tool

# Clean up
cd ../
echo "Releasing DHCP"
sudo dhclient -r ${INTERFACE0}
sudo dhclient -r ${INTERFACE1}
sudo dhclient -r ${INTERFACE2}
sudo dhclient -r ${INTERFACE2}
sudo kill -9 $( cat ${DHCPPIDFILE} )
rm -f ${DHCPPIDFILE}
