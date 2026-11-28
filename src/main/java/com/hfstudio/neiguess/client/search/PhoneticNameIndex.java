package com.hfstudio.neiguess.client.search;

import java.util.List;

import com.asdflj.nech.utils.Match;

import me.towdium.pinin.searchers.Searcher.Logic;
import me.towdium.pinin.searchers.TreeSearcher;

public class PhoneticNameIndex {

    private final TreeSearcher<Integer> prefixes;
    private final TreeSearcher<Integer> substrings;

    public PhoneticNameIndex(String[] names) {
        synchronized (Match.context) {
            prefixes = Match.searcher(Logic.BEGIN);
            substrings = Match.searcher(Logic.CONTAIN);
            for (int index = 0; index < names.length; index++) {
                prefixes.put(names[index], index);
                substrings.put(names[index], index);
            }
        }
    }

    public int[] search(String query, boolean prefixOnly) {
        synchronized (Match.context) {
            List<Integer> matches = (prefixOnly ? prefixes : substrings).search(query);
            int[] result = new int[matches.size()];
            for (int index = 0; index < result.length; index++) result[index] = matches.get(index);
            return result;
        }
    }
}
