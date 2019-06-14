package org.wltea.analyzer.dic;

/**
 * 字典信息描述
 * */
public class DicFile {

    /** 字典名称 */
    private String dicName;

    /** 字典文件路径*/
    private String dicPath;

    /** 是远程文件还是本地字典文件, 默认为本地字典文件*/
    private Boolean isRemote = false;

    private DictType dictType;

    private String absolutePath;

    public DicFile(String absolutePath){
        this.absolutePath = absolutePath;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }
    public String getDicName() {
        return dicName;
    }

    public void setDicName(String dicName) {
        this.dicName = dicName;
    }

    public String getDicPath() {
        return dicPath;
    }

    public void setDicPath(String dicPath) {
        this.dicPath = dicPath;
    }

    public Boolean isRemote() {
        return isRemote;
    }

    public void setRemote(Boolean remote) {
        isRemote = remote;
    }

    public DictType getDictType() {
        return dictType;
    }

    public DicFile setDictType(DictType dictType) {
        this.dictType = dictType;
        return this;
    }

    public enum DictType{
        /**整词*/
        INTACT_WORDS,
        /**量词*/
        QUANTIFIER,
        /**停词*/
        STOPWORDS,
        SUFFIX,
        SURNAME;
    }
}
