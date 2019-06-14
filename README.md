IK Analysis for Elasticsearch
=============================
 

说明
-----
该分词器时基于github medcl的分词器（https://github.com/medcl/elasticsearch-analysis-ik）改造而来，
改造点如下：

1、改造前，所有索引使用一个词库，没办法针对不同索引添加不同词库，
改造后，词库的加载由索引中自定义的analyzer配置时，设置的词库而决定
从而实现了，不同业务的索引使用不同的词库

2、优化了Dictionary类的代码结构，使得逻辑更清晰，将原来600行的代码缩减到300行，
优化比较死板的字典加载机制，不再读取IKAnalyzer.cfg.xml，而直接由用户索引analyzer创建时配置

3、优化了Remote Dictionary的加载机制

4、去掉了分词器中不必要的synchronized锁，提高了性能

5、读取字典文件路径顺序：优先从es的config/analysis-ik/下读取字典文件，
如未找到，则从plugin下，分词器对应的目录读取

### 具体的配置方法如下 Configuration

`IKAnalyzer.cfg.xml` 配置文件不再使用，所有自定义扩展词库需要在定义分词器tokenizer时设置，
例如
###
```
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "tokenizer": {
        "my_tokenizer": {
          "type": "ik_max_word",
          "ext_dic_main": [
            "#dicName$extra#dicPath$extra_test.dic#isRemote$false"
          ]
        }
      },
      "analyzer":{
        "tokenizer":"my_tokenizer",
        "filer":["lowercase", "my_stemmer"]
      }
    }
  }
}
```
由于ES的setting不支持数组josn对象，所以，采用自定义格式配置字典文件
dicName:词典名称
dicPath:词典路径，如果是远程，则为远程路径
isRemote:true:是远程字典文件，false:是本地文件
格式说明：#为字段名称开始符，$是值开始符

联系本人：871057529@qq.com


