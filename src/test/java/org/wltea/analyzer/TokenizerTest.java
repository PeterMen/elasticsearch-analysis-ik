package org.wltea.analyzer;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.settings.Settings;
import org.junit.Test;
import org.wltea.analyzer.cfg.Configuration;
import org.wltea.analyzer.dic.RemoteDicMonitor;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

public class TokenizerTest {

    @Test
    public void testAnalyzer() throws IOException {
        Settings settings =  Settings.builder()
                .put("use_smart", false)
                .put("enable_lowercase", false)
                .put("enable_remote_dict", false)
                .putList("ext_dic_main", Arrays.asList("http://intact.dic"))
                .build();
        Configuration configuration=new Configuration(null,settings) ;

        IKAnalyzer ik =new IKAnalyzer(configuration);


//        String t = "连身裙";
//        String t = "分词器";
        String t = "双肩包";
        TokenStream tokenStream = ik.tokenStream("", new StringReader(t));
        tokenStream.reset();
        CharTermAttribute termAtt  = tokenStream.addAttribute(CharTermAttribute.class);
        while(tokenStream.incrementToken()){
            System.out.println(termAtt);
        }
        tokenStream.end();
        tokenStream.close();
    }

    @Test
    public void testRemoteFileLoad(){

        RemoteDicMonitor.RemoteDicFile remoteDicFile = new RemoteDicMonitor.RemoteDicFile("");
        remoteDicFile.setDicPath("http://intact.dic");

        RemoteDicMonitor monitor = new RemoteDicMonitor();
        System.out.println(monitor.getRemoteWordsUnprivileged(remoteDicFile.getDicPath()));

        monitor.runUnprivileged(remoteDicFile);
    }
}
