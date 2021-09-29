#!/bin/bash

url=$1
file=$2

if [ -z $url ]
then
	url="http://www.doc.ic.ac.uk/~ahanda/living_room_traj2_loop.tgz"
else
	echo "Setting $url"
fi

IFS='/' read -r -a array <<< $url
compressFile=${array[-1]}

echo "FILE : $compressFile"

if [ -z $file ]
then
	file="living_room_traj2_loop.raw"
else
	echo "Setting $file"
fi

echo $url
echo $file

mkdir -p slambench
cd slambench
git clone https://github.com/pamela-project/slambench
cd slambench
make deps
make slambench

cd ..
mkdir -p datasets
cd datasets
wget -O $compressFile $url
tar xzf $compressFile
cd ..

./slambench/build/kfusion/thirdparty/scene2raw datasets $file
mkdir -p ~/.kfusion_tornado/
mv $file  ~/.kfusion_tornado/

echo "done"

