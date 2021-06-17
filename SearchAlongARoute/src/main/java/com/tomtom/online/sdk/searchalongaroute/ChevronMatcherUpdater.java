package com.tomtom.online.sdk.searchalongaroute;

import android.graphics.Color;

import com.tomtom.online.sdk.common.location.LatLng;
import com.tomtom.online.sdk.map.Chevron;
import com.tomtom.online.sdk.map.ChevronPosition;
import com.tomtom.online.sdk.map.CircleBuilder;
import com.tomtom.online.sdk.map.TomtomMap;
import com.tomtom.online.sdk.map.driving.MatchResult;
import com.tomtom.online.sdk.map.driving.MatcherListener;

public class ChevronMatcherUpdater implements MatcherListener {

    private static final int GPS_DOT_COLOR = Color.RED;
    private static final double GPS_DOT_RADIUS = 3.0;
    private static final boolean GPS_DOT_FILL = true;

    private Chevron chevron;
    private TomtomMap tomtomMap;

    public ChevronMatcherUpdater(Chevron chevron, TomtomMap tomtomMap) {
        this.chevron = chevron;
        this.tomtomMap = tomtomMap;
    }

    //tag::doc_process_matcher_result[]
    @Override
    public void onMatched(MatchResult matchResult) {
        ChevronPosition chevronPosition = new ChevronPosition.Builder(matchResult.getMatchedLocation()).build();

        chevron.setDimmed(!matchResult.isMatched());
        chevron.setPosition(chevronPosition);
        chevron.show();

        tomtomMap.getOverlaySettings().removeOverlays();
        tomtomMap.getOverlaySettings().addOverlay(
                CircleBuilder.create()
                        .position(new LatLng(matchResult.originalLocation))
                        .fill(GPS_DOT_FILL)
                        .color(GPS_DOT_COLOR)
                        .radius(GPS_DOT_RADIUS)
                        .build()
        );
    }
    //end::doc_process_matcher_result[]

}