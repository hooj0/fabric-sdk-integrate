#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#
# simple batch script making it easier to cleanup and start a relatively fresh fabric env.

if [ ! -e "docker-compose.yaml" ];then
  echo "docker-compose.yaml not found."
  exit 8
fi

ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION=${ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION:-}

function clean(){

  rm -rf /var/hyperledger/*

  if [ -e "/tmp/HFCSampletest.properties" ];then
    rm -f "/tmp/HFCSampletest.properties"
  fi

  lines=`docker ps -a | grep 'dev-peer' | wc -l`

  if [ "$lines" -gt 0 ]; then
    docker ps -a | grep 'dev-peer' | awk '{print $1}' | xargs docker rm -f
  fi

  lines=`docker images | grep 'dev-peer' | grep 'dev-peer' | wc -l`
  if [ "$lines" -gt 0 ]; then
    docker images | grep 'dev-peer' | awk '{print $1}' | xargs docker rmi -f
  fi

  lines=`docker ps -aq | wc -l`
  if ((lines > 0)); then
  	docker stop $(docker ps -aq)
    docker rm $(docker ps -aq)
  fi
  
  lines=`docker network ls -f 'name=integration_default' | wc -l`
  if ((lines > 1)); then
	  docker network rm integration_default
  fi
}

function up(){

  if [ "$ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION" == "1.0.0" ]; then
    docker-compose up --force-recreate ca0 ca1 peer1.org1.example.com peer1.org2.example.com ccenv
  else
    docker-compose up --force-recreate
  fi
}

function down(){
  docker-compose down;
}

function stop (){
  docker-compose stop;
}

function start (){
  docker-compose start;
}

function upgradeNetwork() {
  docker inspect -f '{{.Config.Volumes}}' orderer.simple.com | grep -q '/var/hyperledger/production/orderer'
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! This network does not appear to be using volumes for its ledgers, did you start from fabric-samples >= v1.1.x?"
    exit 1
  fi

  LEDGERS_BACKUP=./ledgers-backup

  # create ledger-backup directory
  mkdir -p $LEDGERS_BACKUP

  . .env
  echo "IMAGE_TAG_FABRIC: $IMAGE_TAG_FABRIC"	

  # removing the cli container
  # docker-compose stop cli
  # docker-compose up -d --no-deps cli

  echo "Upgrading orderer"
  docker-compose stop orderer.example.com
  docker cp -a orderer.example.com:/var/hyperledger/production/orderer $LEDGERS_BACKUP/orderer.example.com
  docker-compose up -d --no-deps orderer.example.com

  for PEER in peer0.org1.example.com peer1.org1.example.com peer0.org2.example.com peer1.org2.example.com; do
    echo "Upgrading peer $PEER"

    # Stop the peer and backup its ledger
    docker-compose stop $PEER
    docker cp -a $PEER:/var/hyperledger/production $LEDGERS_BACKUP/$PEER/

    # Remove any old containers and images for this peer
    CC_CONTAINERS=$(docker ps | grep dev-$PEER | awk '{print $1}')
    if [ -n "$CC_CONTAINERS" ]; then
      docker rm -f $CC_CONTAINERS
    fi

    CC_IMAGES=$(docker images | grep dev-$PEER | awk '{print $1}')
    if [ -n "$CC_IMAGES" ]; then
      docker rmi -f $CC_IMAGES
    fi

    # Start the peer again
    docker-compose up -d --no-deps $PEER
  done
}

for opt in "$@"
do

    case "$opt" in
        up)
            up
            ;;
        down)
            down
            ;;
        stop)
            stop
            ;;
        start)
            start
            ;;
        clean)
            clean
            ;;
        restart)
            down
            clean
            up
            ;;

        *)
            echo $"Usage: $0 {up|down|start|stop|clean|restart}"
            exit 1

esac
done
