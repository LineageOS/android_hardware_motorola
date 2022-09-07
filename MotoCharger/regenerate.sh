#!/bin/bash

if [ -z "$(which convert)" ] || [ -z "$(which pngcrush)" ]; then
    echo "Please install imagemagick and pngcrush"
    exit 1
fi

for RESOLUTION in 540x960 720x1280 720x1440 720x1512 720x1570 876x2142 1080x1920 1080x2160 1080x2246 1080x2270 1080x2340 1080x2360 1080x2400 1080x2520 1440x2560; do
    WIDTH=$(echo $RESOLUTION | cut -f1 -d 'x')
    HEIGHT=$(echo $RESOLUTION | cut -f2 -d 'x')

    rm -rf $RESOLUTION
    mkdir $RESOLUTION

    for SVG in svg/*.svg; do
        PNG="$RESOLUTION/$(basename $SVG | cut -f1 -d '.').png"
        convert -resize ${WIDTH}x${HEIGHT}! $SVG $PNG
    done

    SCALEFILE="$RESOLUTION/battery_scale.png"
    SCALEFILES="$(ls $RESOLUTION/battery_scale_*.png)"
    FRAMES="$(ls -l $SCALEFILES | wc -l)"
    SCALEHEIGHT=$(($HEIGHT * $FRAMES))

    convert -size ${WIDTH}x${SCALEHEIGHT} canvas:black $SCALEFILES -fx "u[j%$FRAMES+1].p{i,int(j/$FRAMES)}" png24:$SCALEFILE.tmp
    pngcrush -text b "Frames" "$FRAMES" $SCALEFILE.tmp $SCALEFILE
    rm $SCALEFILES $SCALEFILE.tmp
done
