#!/bin/bash
# 同时起 Python(8080) 和 Java(8081)，用于并排对比
set -e
java -cp /tmp/prootsrv/cls Main > /tmp/prootsrv.log 2>&1 &
(cd /root/workspace/huluwa/mock && python3 server.py > /tmp/py_srv.log 2>&1 &)
sleep 5
echo "python 8080 / java 8081 已启动"
