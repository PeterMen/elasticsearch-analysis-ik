/**
 * 
 */
package org.wltea.analyzer.cfg;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugin.analysis.ik.AnalysisIkPlugin;
import org.wltea.analyzer.dic.DicFile;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Configuration {


	private static final String PATH_DIC_MAIN = "main.dic";
	private static final String PATH_DIC_SURNAME = "surname.dic";
	private static final String PATH_DIC_QUANTIFIER = "quantifier.dic";
	private static final String PATH_DIC_SUFFIX = "suffix.dic";
	private static final String PATH_DIC_PREP = "preposition.dic";
	private static final String PATH_DIC_STOP = "stopword.dic";
	// 要使用的词典文件
	private List<DicFile> dicFiles = new ArrayList<>();

	//是否启用智能分词
	private  boolean useSmart;

	//是否启用远程词典加载
	private boolean enableRemoteDict=false;

	//是否启用小写处理
	private boolean enableLowercase=true;
	// 用于读取插件绝对路径下文件
	private String absolutePath;

	/**
	 * settings是分词器定义时的配置信息
	 * */
	@Inject
	public Configuration(Environment env,Settings settings) {
		this.absolutePath = env.configFile().resolve(AnalysisIkPlugin.PLUGIN_NAME).toAbsolutePath().toString();
//		this.absolutePath = "C:\\Users\\jm005113\\Desktop\\workspace\\elasticsearch-analysis-ik\\config";
		this.useSmart = settings.get("use_smart", "false").equals("true");
		this.enableLowercase = settings.get("enable_lowercase", "true").equals("true");
		this.enableRemoteDict = settings.get("enable_remote_dict", "true").equals("true");

		// 以下部分为初始化分词器配置的词典文件
		// 基础整词（必选词典文件）
		DicFile mainDic = new DicFile(absolutePath);
		mainDic.setDicName("main");
		mainDic.setDicPath(PATH_DIC_MAIN);
		mainDic.setRemote(false);
		mainDic.setDictType(DicFile.DictType.INTACT_WORDS);
		this.dicFiles.add(mainDic);

		// 基础量词（必选词典文件）
		DicFile quantifierDic = new DicFile(absolutePath);
		quantifierDic.setDicName("quantifier");
		quantifierDic.setDicPath(PATH_DIC_QUANTIFIER);
		quantifierDic.setRemote(false);
		quantifierDic.setDictType(DicFile.DictType.QUANTIFIER);
		this.dicFiles.add(quantifierDic);

		// 基础停词（必选词典文件）
		DicFile stopwordsDic = new DicFile(absolutePath);
		stopwordsDic.setDicName("stopwords");
		stopwordsDic.setDicPath(PATH_DIC_STOP);
		stopwordsDic.setRemote(false);
		stopwordsDic.setDictType(DicFile.DictType.STOPWORDS);
		this.dicFiles.add(stopwordsDic);

		// 基础前缀词（必选词典文件）
		DicFile suffixDic = new DicFile(absolutePath);
		suffixDic.setDicName("suffix");
		suffixDic.setDicPath(PATH_DIC_SUFFIX);
		suffixDic.setRemote(false);
		suffixDic.setDictType(DicFile.DictType.SUFFIX);
		this.dicFiles.add(suffixDic);

		// 基础前姓氏（必选词典文件）
		DicFile surnameDic = new DicFile(absolutePath);
		surnameDic.setDicName("surname");
		surnameDic.setDicPath(PATH_DIC_SURNAME);
		surnameDic.setRemote(false);
		surnameDic.setDictType(DicFile.DictType.SURNAME);
		this.dicFiles.add(surnameDic);

		// 配置用户设置的词典文件
		List<String> mainDics = settings.getAsList("ext_dic_main");
		if(mainDics != null && mainDics.size() > 0 ){
			mainDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.INTACT_WORDS)));
		}
		// 配置用户设置的词典文件
		List<String> stopDics = settings.getAsList("ext_dic_stop");
		if(stopDics != null && stopDics.size() > 0 ){
			stopDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.STOPWORDS)));
		}
		// 配置用户设置的词典文件
		List<String> quantifierDics = settings.getAsList("ext_dic_quantifier");
		if(quantifierDics != null && quantifierDics.size() > 0 ){
			quantifierDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.QUANTIFIER)));
		}
	}

	/**
	 * 解析配置好的词典文件，示例：#dicName$extra#dicPath$extra_test.dic#isRemote$false
	 * 解析说明：#为key的开始，$是value的开始
	 * */
	private static DicFile str2DicFile(String absolutePath, String  dicPath){
		DicFile dicFile = new DicFile(absolutePath);
		dicFile.setRemote(dicPath.startsWith("http:") || dicPath.startsWith("https:") || dicPath.startsWith("ftp:"));
		dicFile.setDicName(getMD5(dicPath));
		dicFile.setDicPath(dicPath);
		return dicFile;
	}

	public static String getMD5(String string) {
		byte[] hash;
		try {
			//创建一个MD5算法对象，并获得MD5字节数组,16*8=128位
			hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Huh, MD5 should be supported?", e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Huh, UTF-8 should be supported?", e);
		}

		//转换为十六进制字符串
		StringBuilder hex = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			if ((b & 0xFF) < 0x10) hex.append("0");
			hex.append(Integer.toHexString(b & 0xFF));
		}
		return hex.toString().toLowerCase();
	}

	public Path getConfigInPluginDir() {
		return PathUtils
				.get(new File(AnalysisIkPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath())
						.getParent(), "config")
				.toAbsolutePath();
	}

	public boolean isUseSmart() {
		return useSmart;
	}

	public Configuration setUseSmart(boolean useSmart) {
		this.useSmart = useSmart;
		return this;
	}

	public boolean isEnableRemoteDict() {
		return enableRemoteDict;
	}

	public boolean isEnableLowercase() {
		return enableLowercase;
	}

	public List<DicFile> getDicFiles() {
		return dicFiles;
	}

	public void addDic(List<DicFile> dicFiles) {
		this.dicFiles.addAll(dicFiles);
	}
}
