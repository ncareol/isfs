#!/bin/sh

g++ -I/opt/nidas/include -L/opt/nidas/lib -lnidas_util udp_send.cc -o udp_send
