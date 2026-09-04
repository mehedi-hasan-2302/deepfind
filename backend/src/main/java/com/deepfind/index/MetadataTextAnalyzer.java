package com.deepfind.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;

final class MetadataTextAnalyzer extends Analyzer {

    private static final int WORD_DELIMITER_FLAGS = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
            | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
            | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
            | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
            | WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE
            | WordDelimiterGraphFilter.PRESERVE_ORIGINAL;

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream delimited = new WordDelimiterGraphFilter(tokenizer, WORD_DELIMITER_FLAGS, null);
        return new TokenStreamComponents(tokenizer, new LowerCaseFilter(delimited));
    }
}
