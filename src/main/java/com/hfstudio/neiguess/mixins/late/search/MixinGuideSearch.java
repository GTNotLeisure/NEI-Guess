package com.hfstudio.neiguess.mixins.late.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.hfstudio.guidenh.guide.internal.search.GuideSearch;
import com.hfstudio.guidenh.shadow.org.apache.lucene.analysis.Analyzer;
import com.hfstudio.guidenh.shadow.org.apache.lucene.document.Document;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.IndexSearcher;
import com.hfstudio.guidenh.shadow.org.apache.lucene.search.Query;
import com.hfstudio.neiguess.client.search.GuideQueryBridge;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(value = GuideSearch.class, remap = false)
public abstract class MixinGuideSearch {

    @Shadow
    private IndexSearcher indexSearcher;

    @WrapOperation(
        method = "searchGuide",
        at = @At(
            value = "INVOKE",
            target = "Lcom/hfstudio/guidenh/guide/internal/search/GuideQueryParser;parse(Ljava/lang/String;Lcom/hfstudio/guidenh/shadow/org/apache/lucene/analysis/Analyzer;Ljava/util/Collection;)Lcom/hfstudio/guidenh/shadow/org/apache/lucene/search/Query;"),
        require = 1)
    private Query neiguess$extendSearch(String query, Analyzer analyzer, Collection<String> languages,
        Operation<Query> original) {
        return GuideQueryBridge.augment(original.call(query, analyzer, languages), query, indexSearcher);
    }

    @ModifyReturnValue(method = "createPageDocument", at = @At("RETURN"), require = 1)
    private Document neiguess$indexPageIdentity(Document document) {
        return GuideQueryBridge.index(document);
    }

    @ModifyReturnValue(method = "createFingerprintDigest", at = @At("RETURN"), require = 1)
    private static MessageDigest neiguess$versionClientIndex(MessageDigest digest) {
        digest.update("neiguess-search-2".getBytes(StandardCharsets.UTF_8));
        return digest;
    }
}
