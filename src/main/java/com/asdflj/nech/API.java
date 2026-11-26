package com.asdflj.nech;

import com.asdflj.nech.utils.Match;

import me.towdium.pinin.PinIn;

public class API {

    public static API INSTANCE = new API();

    public PinIn getPinIn() {
        return Match.context;
    }

    public boolean contains(String text, String query) {
        return Match.contains(text, query);
    }

    public boolean contains(String text, CharSequence query) {
        return Match.contains(text, query);
    }

    public boolean contains(CharSequence text, CharSequence query, boolean ignoreCase) {
        return Match.contains(text, query, ignoreCase);
    }

    public boolean equals(String text, Object query) {
        return Match.equals(text, query);
    }

    public boolean contains(CharSequence text, CharSequence query) {
        return Match.contains(text, query);
    }

}
