package org.wltea.analyzer;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.settings.Settings;
import org.wltea.analyzer.cfg.Configuration;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

public class TokenizerTest {

    public static void main(String[] args) throws IOException {
        Settings settings =  Settings.builder()
                .put("use_smart", true)
                .put("enable_lowercase", false)
                .put("enable_remote_dict", false)
                .putList("ext_dic_main", Arrays.asList("#dicName$extra#dicPath$extra_test.dic#isRemote$false"))
                .build();
        Configuration configuration=new Configuration(null,settings).setUseSmart(true);

        IKAnalyzer ik =new IKAnalyzer(configuration);


        String t = "IK分词器Lucene Analyzer接口实现类 民生银行 我是中国人";
//        String t = "分词器";
        TokenStream tokenStream = ik.tokenStream("", new StringReader(t));
        tokenStream.reset();
        CharTermAttribute termAtt  = tokenStream.addAttribute(CharTermAttribute.class);
        while(tokenStream.incrementToken()){
            System.out.println(termAtt);
        }
        tokenStream.end();
        tokenStream.close();
    }
}
