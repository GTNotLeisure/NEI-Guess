package com.hfstudio.neiguess.client.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import com.asdflj.nech.utils.Match;
import com.hfstudio.guidenh.shadow.org.apache.lucene.document.Document;
import com.hfstudio.guidenh.shadow.org.apache.lucene.document.Field;
import com.hfstudio.guidenh.shadow.org.apache.lucene.document.StoredField;
import com.hfstudio.guidenh.shadow.org.apache.lucene.document.StringField;
import com.hfstudio.guidenh.shadow.org.apache.lucene.index.IndexReader;
import com.hfstudio.guidenh.shadow.org.apache.lucene.index.IndexableField;
import com.hfstudio.guidenh.shadow.org.apache.lucene.index.MultiBits;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.BooleanClause.Occur;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.BooleanQuery;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.IndexSearcher;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.Query;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.TermInSetQuery;
import com.hfstudio.guidenh.shadow.org.apache.lucene.util.Bits;
import com.hfstudio.guidenh.shadow.org.apache.lucene.util.BytesRef;
import com.hfstudio.neiguess.NEIGuess;

import codechicken.nei.LRUCache;

public class GuideQueryBridge {

    private static final String PAGE_ID = "neiguess_page_identity";
    private static final String PAGE_KEYWORDS = "neiguess_page_keywords";
    private static final Map<IndexReader, PageIndex> readers = new WeakHashMap<>();

    private GuideQueryBridge() {}

    public static Document index(Document document) {
        if (document != null) {
            String guide = document.get("guide_id");
            String page = document.get("page_id");
            if (guide != null && page != null) {
                String identity = guide + '\0' + page + '\0' + document.get("lang");
                StringBuilder keywords = new StringBuilder();
                for (IndexableField field : document.getFields()) {
                    String name = field.name();
                    if (name.startsWith("page_keyword_")) {
                        String value = field.stringValue();
                        if (value != null) keywords.append('\n')
                            .append(value);
                    }
                }
                document.add(new StringField(PAGE_ID, identity, Field.Store.YES));
                document.add(new StoredField(PAGE_KEYWORDS, keywords.toString()));
            }
        }
        return document;
    }

    public static Query augment(Query original, String query, IndexSearcher searcher) {
        List<String> terms = Match.queryTerms(query);
        if (searcher == null || terms.isEmpty()) return original;
        try {
            IndexReader reader = searcher.getIndexReader();
            PageIndex index;
            synchronized (readers) {
                index = readers.get(reader);
                if (index == null) {
                    index = new PageIndex(readPages(reader, searcher));
                    readers.put(reader, index);
                }
            }
            List<BytesRef> matches = index.matches(query, terms);
            if (matches.isEmpty()) return original;
            // A filter-only alternative preserves native scores and guide constraints.
            Query fallback = new BooleanQuery.Builder().add(new TermInSetQuery(PAGE_ID, matches), Occur.FILTER)
                .build();
            return new BooleanQuery.Builder().add(original, Occur.SHOULD)
                .add(fallback, Occur.SHOULD)
                .build();
        } catch (IOException failure) {
            NEIGuess.LOG.warn("Unable to extend a GuideNH search query", failure);
            return original;
        }
    }

    private static List<Page> readPages(IndexReader reader, IndexSearcher searcher) throws IOException {
        List<Page> pages = new ArrayList<>();
        Bits live = MultiBits.getLiveDocs(reader);
        for (int index = 0; index < reader.maxDoc(); index++) {
            if (live != null && !live.get(index)) continue;
            Document document = searcher.doc(index);
            String identity = document.get(PAGE_ID);
            if (identity == null) continue;
            String text = (Objects.toString(document.get("page_id"), "") + '\n'
                + Objects.toString(document.get("page_title"), "")
                + '\n'
                + Objects.toString(document.get("page_content"), "")
                + '\n'
                + Objects.toString(document.get(PAGE_KEYWORDS), "")).toLowerCase(Locale.ROOT);
            pages.add(new Page(identity, text));
        }
        return List.copyOf(pages);
    }

    private static class PageIndex {

        private final List<Page> pages;
        private final LRUCache<QueryKey, List<BytesRef>> queries = new LRUCache<>(32);

        private PageIndex(List<Page> pages) {
            this.pages = pages;
        }

        private synchronized List<BytesRef> matches(String query, List<String> terms) {
            QueryKey key = new QueryKey(query, Match.revision(), ClientSearchAliases.INSTANCE.revision());
            List<BytesRef> matches = queries.get(key);
            if (matches == null) {
                List<BytesRef> found = new ArrayList<>();
                for (Page page : pages) {
                    if (Match.matchesTerms(page.text, terms)) found.add(new BytesRef(page.identity));
                }
                matches = List.copyOf(found);
                queries.put(key, matches);
            }
            return matches;
        }
    }

    private record Page(String identity, String text) {}

    private record QueryKey(String query, long phonetics, long aliases) {}
}
