#!/bin/bash

folder=$1
for f in `ls $folder`
do
    if [ `echo $f | grep -P "method\d+\.dot"` ]; then
        id=`echo $f | sed 's/method\([0-9]\+\)\.dot/\1/'`
        echo "Processing file number ${id}"
        dot -Tpdf -Nshape=box ${folder}/$f -o ${folder}/m${id}.pdf
        #dot -Tpdf -Granksep=2.5 -Gnodesep=1 -Nshape=box ${folder}/$f -o ${folder}/m${id}.pdf
    fi
done
