#!/bin/sh
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