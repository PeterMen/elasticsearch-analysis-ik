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
import java.nio.file.Path;
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

	@Inject
	public Configuration(Environment env,Settings settings) {
		this.absolutePath = env.configFile().resolve(AnalysisIkPlugin.PLUGIN_NAME).toAbsolutePath().toString();
//		this.absolutePath = "C:\\Users\\jm005113\\Desktop\\workspace\\elasticsearch-analysis-ik\\config";
		this.useSmart = settings.get("use_smart", "false").equals("true");
		this.enableLowercase = settings.get("enable_lowercase", "true").equals("true");
		this.enableRemoteDict = settings.get("enable_remote_dict", "true").equals("true");

		// 基础整词
		DicFile mainDic = new DicFile(absolutePath);
		mainDic.setDicName("main");
		mainDic.setDicPath(PATH_DIC_MAIN);
		mainDic.setRemote(false);
		mainDic.setDictType(DicFile.DictType.INTACT_WORDS);
		this.dicFiles.add(mainDic);

		// 基础量词
		DicFile quantifierDic = new DicFile(absolutePath);
		quantifierDic.setDicName("quantifier");
		quantifierDic.setDicPath(PATH_DIC_QUANTIFIER);
		quantifierDic.setRemote(false);
		quantifierDic.setDictType(DicFile.DictType.QUANTIFIER);
		this.dicFiles.add(quantifierDic);

		// 基础停词
		DicFile stopwordsDic = new DicFile(absolutePath);
		stopwordsDic.setDicName("stopwords");
		stopwordsDic.setDicPath(PATH_DIC_STOP);
		stopwordsDic.setRemote(false);
		stopwordsDic.setDictType(DicFile.DictType.STOPWORDS);
		this.dicFiles.add(stopwordsDic);

		// 基础前缀词
		DicFile suffixDic = new DicFile(absolutePath);
		suffixDic.setDicName("suffix");
		suffixDic.setDicPath(PATH_DIC_SUFFIX);
		suffixDic.setRemote(false);
		suffixDic.setDictType(DicFile.DictType.SUFFIX);
		this.dicFiles.add(suffixDic);

		// 基础前姓氏
		DicFile surnameDic = new DicFile(absolutePath);
		surnameDic.setDicName("surname");
		surnameDic.setDicPath(PATH_DIC_SURNAME);
		surnameDic.setRemote(false);
		surnameDic.setDictType(DicFile.DictType.SURNAME);
		this.dicFiles.add(surnameDic);

		List<String> mainDics = settings.getAsList("ext_dic_main");
		if(mainDics != null && mainDics.size() > 0 ){
			// 配置用户设置的词典文件
			mainDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.INTACT_WORDS)));
		}
		List<String> stopDics = settings.getAsList("ext_dic_stop");
		if(stopDics != null && stopDics.size() > 0 ){
			// 配置用户设置的词典文件
			stopDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.STOPWORDS)));
		}
		List<String> quantifierDics = settings.getAsList("ext_dic_quantifier");
		if(quantifierDics != null && quantifierDics.size() > 0 ){
			// 配置用户设置的词典文件
			quantifierDics.forEach(dicFileStr -> this.dicFiles.add(str2DicFile(absolutePath, dicFileStr).setDictType(DicFile.DictType.QUANTIFIER)));
		}
	}

	private DicFile str2DicFile(String absolutePath, String  str){
		char[] fieldName = new char[50];
		char[] fieldValue = new char[200];
		DicFile dicFile = new DicFile(absolutePath);
		boolean isName = true;
		int tmpCursor = 0;
		int keyValueFull = 0;
		for(int i=0; i<str.length(); i++){
			char tmpChar = str.charAt(i);
			if(tmpChar == '#'){
				if(keyValueFull == 2){
					setDicFile(fieldName, fieldValue, dicFile);
					// reset
					fieldName = new char[50];
					fieldValue = new char[200];
					keyValueFull=0;
				}
				keyValueFull++;
				tmpCursor = 0;
				isName = true;
				continue;
			} else if(tmpChar == '$'){
				keyValueFull++;
				tmpCursor = 0;
				isName = false;
				continue;
			} else {
				if(isName)fieldName[tmpCursor++] = tmpChar;
				else fieldValue[tmpCursor++] = tmpChar;
				if(str.length() == i+1) setDicFile(fieldName, fieldValue, dicFile);
			}
		}
		return dicFile;
	}

	private void setDicFile(char[] fieldName, char[] fieldValue, DicFile dicFile) {
		if("dicName".equals(String.valueOf(fieldName).trim())){
			dicFile.setDicName(String.valueOf(fieldValue).trim());
		} else if("dicPath".equals(String.valueOf(fieldName).trim())){
			dicFile.setDicPath(String.valueOf(fieldValue).trim());
		} else if("isRemote".equals(String.valueOf(fieldName).trim())){
			dicFile.setRemote(Boolean.valueOf(String.valueOf(fieldValue).trim()));
		}
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
