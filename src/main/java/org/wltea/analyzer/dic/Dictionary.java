/**
 * IK 中文分词  版本 5.0
 * IK Analyzer release 5.0
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * 源代码由林良益(linliangyi2005@gmail.com)提供
 * 版权声明 2012，乌龙茶工作室
 * provided by Linliangyi and copyright 2012 by Oolong studio
 *
 *
 */
package org.wltea.analyzer.dic;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.io.PathUtils;
import org.wltea.analyzer.help.ESPluginLoggerFactory;


/**
 * 词典管理类,单子模式
 */
public class Dictionary {

	/*
	 * 词典单子实例
	 */
	private static Dictionary singleton;
	/*
	 * 主词典对象
	 */
	private Map<String, DictSegment> _MainDict = new HashMap<>(4);
	/*
	 * 量词词典
	 */
	private Map<String, DictSegment> _QuantifierDict = new HashMap<>(4);
	/*
	 * 停止词集合
	 */
	private Map<String, DictSegment> _StopWords = new HashMap<>(4);
	/*
	 * 姓氏词典
	 */
	private Map<String, DictSegment> _SurnameDict = new HashMap<>(4);
	/*
	 * 后缀词典
	 */
	private Map<String, DictSegment> _SuffixDict = new HashMap<>(4);
	/*
	 * 副词，介词词典
	 */
	private Map<String, DictSegment> _PrepDict = new HashMap<>(4);

	private static final Logger logger = ESPluginLoggerFactory.getLogger(RemoteDicMonitor.class.getName());

	private static ScheduledExecutorService pool;

	private RemoteDicMonitor dicMonitor;

	private Dictionary(){}

	public void loadAllDictFiles(List<DicFile> dicFiles) {
		dicFiles.forEach(dicFile -> {
			if(needLoad(dicFile)){
				DictSegment dictSegment;
				if(dicFile.isRemote()){
					// 从远程加载
					dictSegment = RemoteDicMonitor.loadRemoteDic(dicFile);
					// 添加监控任务
					addMonitorTask(dicFile);
				} else {
					dictSegment = loadLocalDictFile(dicFile);
				}
				if(dicFile.getDictType() == DicFile.DictType.INTACT_WORDS){
					_MainDict.put(dicFile.getDicName(), dictSegment);
				} else if(dicFile.getDictType() == DicFile.DictType.QUANTIFIER){
					_QuantifierDict.put(dicFile.getDicName(), dictSegment);
				} else if(dicFile.getDictType() == DicFile.DictType.STOPWORDS){
					_StopWords.put(dicFile.getDicName(), dictSegment);
				} else if(dicFile.getDictType() == DicFile.DictType.SUFFIX){
					_SuffixDict.put(dicFile.getDicName(), dictSegment);
				} else if(dicFile.getDictType() == DicFile.DictType.SURNAME){
					_SurnameDict.put(dicFile.getDicName(), dictSegment);
				}
			}
		});
	}

	private void addMonitorTask(DicFile dicFile) {
		if(pool == null){
			synchronized (pool){
				if(pool == null){
					// 初始化监控任务
					initRemoteMoniter();
				}
			}
		}
		RemoteDicMonitor.RemoteDicFile remoteDicFile = new RemoteDicMonitor.RemoteDicFile(dicFile.getAbsolutePath());
		remoteDicFile.setDicName(dicFile.getDicName());
		remoteDicFile.setDicPath(dicFile.getDicPath());
		remoteDicFile.setDictType(dicFile.getDictType());
		remoteDicFile.setRemote(true);
		this.dicMonitor.addFile(remoteDicFile);
	}

	private boolean needLoad(DicFile dicFile){
		if(dicFile.getDictType() == DicFile.DictType.INTACT_WORDS){
			return _MainDict.get(dicFile.getDicName()) == null;
		} else if(dicFile.getDictType() == DicFile.DictType.QUANTIFIER){
			return _QuantifierDict.get(dicFile.getDicName()) == null;
		} else if(dicFile.getDictType() == DicFile.DictType.STOPWORDS){
			return _StopWords.get(dicFile.getDicName()) == null;
		} else if(dicFile.getDictType() == DicFile.DictType.SUFFIX){
			return _SuffixDict.get(dicFile.getDicName()) == null;
		} else if(dicFile.getDictType() == DicFile.DictType.SURNAME){
			return _SurnameDict.get(dicFile.getDicName()) == null;
		}
		return false;
	}

	private static DictSegment loadLocalDictFile(DicFile dicFile) {
		DictSegment dictSegment = new DictSegment((char) 0);

        // check file exist
        // 读取字典文件路径顺序：优先从es的config/analysis-ik/下读取字典文件，
        // 如未找到，则从plugin下，分词器对应的目录读取
        Path dicFilePath = Paths.get(dicFile.getAbsolutePath(), dicFile.getDicPath());
        if(!Files.exists(dicFilePath)){
            Path configInPluginDir = PathUtils.get(new File(Dictionary.class.getProtectionDomain().getCodeSource().getLocation().getPath())
 				.getParent(), "config").toAbsolutePath();
            dicFilePath = configInPluginDir.resolve(dicFile.getDicPath());
        }
		// 读取词典文件
		try (InputStream is = new FileInputStream(dicFilePath.toFile());
			 BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"), 512)){
			String word = br.readLine();
			if (word != null) {
				if (word.startsWith("\uFEFF"))
					word = word.substring(1);
				for (; word != null; word = br.readLine()) {
					word = word.trim();
					if (word.isEmpty()) continue;
					dictSegment.fillSegment(word.toCharArray());
				}
			}
		} catch (FileNotFoundException e) {
			logger.error("ik-analyzer: " + dicFile.getDicName() + " not found", e);
			throw new RuntimeException("ik-analyzer: " + dicFile.getDicName() + " not found!!!", e);
		} catch (IOException e) {
			logger.error("ik-analyzer: " + dicFile.getDicName() + " loading failed", e);
		}
		return dictSegment;
	}

	/**
	 * 获取词典单子实例
	 * 
	 * @return Dictionary 单例对象
	 */
	public static Dictionary getSingleton()  {
		if (singleton == null) {
			synchronized (Dictionary.class){
				if(singleton == null){
					singleton = new Dictionary();
				}
			}
		}
		return singleton;
	}

	public static void initRemoteMoniter(){
		// 开启远程词典文件监控任务
		singleton.dicMonitor = new RemoteDicMonitor();
		pool = Executors.newScheduledThreadPool(1);
		pool.scheduleAtFixedRate(singleton.dicMonitor, 10, 60, TimeUnit.SECONDS);
	}


	/**
	 * 批量加载新词条
	 * 
	 * @param words
	 *            Collection<String>词条列表
	 */
	public void addWords(String fileName, Collection<String> words) {
		if (words != null) {
			for (String word : words) {
				if (word != null) {
					// 批量加载词条到主内存词典中
					singleton._MainDict.get(fileName).fillSegment(word.trim().toCharArray());
				}
			}
		}
	}

	/**
	 * 批量移除（屏蔽）词条
	 */
	public void disableWords(String fileName, Collection<String> words) {
		if (words != null) {
			for (String word : words) {
				if (word != null) {
					// 批量屏蔽词条
					singleton._MainDict.get(fileName).disableSegment(word.trim().toCharArray());
				}
			}
		}
	}

	/**
	 * 检索匹配主词典
	 * 
	 * @return Hit 匹配结果描述
	 */
	public Hit matchInMainDict(String fileName, char[] charArray) {
		return singleton._MainDict.get(fileName).match(charArray);
	}

	/**
	 * 检索匹配主词典
	 * 
	 * @return Hit 匹配结果描述
	 */
	public Hit matchInMainDict(List<String> dicNames, char[] charArray, int begin, int length) {
		Hit tmpHit = new Hit();
		for(String dicName : dicNames){
			// 成词优先级比前缀优先级高
			tmpHit = singleton._MainDict.get(dicName).match(charArray, begin, length);
			if(tmpHit.isMatch() || tmpHit.isPrefix()) return tmpHit;
		}
		return tmpHit;
	}

	/**
	 * 检索匹配量词词典
	 * 
	 * @return Hit 匹配结果描述
	 */
	public Hit matchInQuantifierDict(List<String> fileNames, char[] charArray, int begin, int length) {
		Hit tmpHit = new Hit();
		for(String fileName : fileNames){
			// 成词优先级比前缀优先级高
			tmpHit = singleton._QuantifierDict.get(fileName).match(charArray, begin, length);
			if(tmpHit.isMatch() || tmpHit.isPrefix()) return tmpHit;
		}
		return tmpHit;
	}

	/**
	 * 从已匹配的Hit中直接取出DictSegment，继续向下匹配
	 * 
	 * @return Hit
	 */
	public Hit matchWithHit(char[] charArray, int currentIndex, Hit matchedHit) {
		DictSegment ds = matchedHit.getMatchedDictSegment();
		return ds.match(charArray, currentIndex, 1, matchedHit);
	}

	/**
	 * 判断是否是停止词
	 * 
	 * @return boolean
	 */
	public boolean isStopWord(List<String> fileNames, char[] charArray, int begin, int length) {
		for(String fileName : fileNames){
			// 满足任意词典里的停词，则认为是停词，都不满足，则不是停词
			if(singleton._StopWords.get(fileName).match(charArray, begin, length).isMatch())
				return true;
		}
		return false;
	}

	/**
	 * 检索匹配姓氏词典
	 * @param charArray
	 * @param begin
	 * @param length
	 * @return Hit 匹配结果描述
	 */
	public static Hit matchInSurnameDict(String fileName, char[] charArray , int begin, int length){
		return singleton._SurnameDict.get(fileName).match(charArray, begin, length);
	}

	/**
	 * 检索匹配在后缀词典
	 * @param charArray
	 * @param begin
	 * @param length
	 * @return Hit 匹配结果描述
	 */
	public static Hit matchInSuffixDict(String fileName, char[] charArray , int begin, int length){
		return singleton._SuffixDict.get(fileName).match(charArray, begin, length);
	}

	/**
	 * 检索匹配介词、副词词典
	 * @param charArray
	 * @param begin
	 * @param length
	 * @return  Hit 匹配结果描述
	 */
	public static Hit matchInPrepDict(String fileName, char[] charArray , int begin, int length){
		return singleton._PrepDict.get(fileName).match(charArray, begin, length);
	}
}
