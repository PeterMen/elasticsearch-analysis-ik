IK Analysis for Elasticsearch
=============================

The IK Analysis plugin integrates Lucene IK analyzer (http://code.google.com/p/ik-analyzer/) into elasticsearch, support customized dictionary.

Analyzer: `ik_smart` , `ik_max_word` , Tokenizer: `ik_smart` , `ik_max_word`

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


### Dictionary Configuration

`IKAnalyzer.cfg.xml` 配置文件不再使用，所有自定义扩展词库需要在定义分词器时设置，
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
            "https:xxx.com/sss/ssss.dic",// 该字典是一个远程字典，路径以http或https打头
            "dddd.dic"// 该词典文件时ES服务器上的本地文件，需要放到IK的config目录下
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
Versions
--------

IK version | ES version
-----------|-----------
master | 6.x 

其它版本，请自己修改version，打包即可

Install
-------

1.download or compile

* optional 1 - download pre-build package from here: https://github.com/medcl/elasticsearch-analysis-ik/releases

    create plugin folder `cd your-es-root/plugins/ && mkdir ik`
    
    unzip plugin to folder `your-es-root/plugins/ik`

* optional 2 - use elasticsearch-plugin to install ( supported from version v5.5.1 ):

    ```
    ./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v6.3.0/elasticsearch-analysis-ik-6.3.0.zip
    ```

   NOTE: replace `6.3.0` to your own elasticsearch version

2.restart elasticsearch



#### Quick Example

1.create a index

```bash
curl -XPUT http://localhost:9200/index
```

2.create a mapping

```bash
curl -XPOST http://localhost:9200/index/_mapping -H 'Content-Type:application/json' -d'
{
        "properties": {
            "content": {
                "type": "text",
                "analyzer": "ik_max_word",
                "search_analyzer": "ik_smart"
            }
        }

}'
```

3.index some docs

```bash
curl -XPOST http://localhost:9200/index/_create/1 -H 'Content-Type:application/json' -d'
{"content":"美国留给伊拉克的是个烂摊子吗"}
'
```

```bash
curl -XPOST http://localhost:9200/index/_create/2 -H 'Content-Type:application/json' -d'
{"content":"公安部：各地校车将享最高路权"}
'
```

```bash
curl -XPOST http://localhost:9200/index/_create/3 -H 'Content-Type:application/json' -d'
{"content":"中韩渔警冲突调查：韩警平均每天扣1艘中国渔船"}
'
```

```bash
curl -XPOST http://localhost:9200/index/_create/4 -H 'Content-Type:application/json' -d'
{"content":"中国驻洛杉矶领事馆遭亚裔男子枪击 嫌犯已自首"}
'
```

4.query with highlighting

```bash
curl -XPOST http://localhost:9200/index/_search  -H 'Content-Type:application/json' -d'
{
    "query" : { "match" : { "content" : "中国" }},
    "highlight" : {
        "pre_tags" : ["<tag1>", "<tag2>"],
        "post_tags" : ["</tag1>", "</tag2>"],
        "fields" : {
            "content" : {}
        }
    }
}
'
```

Result

```json
{
    "took": 14,
    "timed_out": false,
    "_shards": {
        "total": 5,
        "successful": 5,
        "failed": 0
    },
    "hits": {
        "total": 2,
        "max_score": 2,
        "hits": [
            {
                "_index": "index",
                "_type": "fulltext",
                "_id": "4",
                "_score": 2,
                "_source": {
                    "content": "中国驻洛杉矶领事馆遭亚裔男子枪击 嫌犯已自首"
                },
                "highlight": {
                    "content": [
                        "<tag1>中国</tag1>驻洛杉矶领事馆遭亚裔男子枪击 嫌犯已自首 "
                    ]
                }
            },
            {
                "_index": "index",
                "_type": "fulltext",
                "_id": "3",
                "_score": 2,
                "_source": {
                    "content": "中韩渔警冲突调查：韩警平均每天扣1艘中国渔船"
                },
                "highlight": {
                    "content": [
                        "均每天扣1艘<tag1>中国</tag1>渔船 "
                    ]
                }
            }
        ]
    }
}
```
 


### 热更新 IK 分词使用方法

 
满足上面两点要求就可以实现热更新分词了，不需要重启 ES 实例。

可以将需自动更新的热词放在一个 UTF-8 编码的 .txt 文件里，放在 nginx 或其他简易 http server 下，当 .txt 文件修改时，http server 会在客户端请求该文件时自动返回相应的 Last-Modified 和 ETag。可以另外做一个工具来从业务系统提取相关词汇，并更新这个 .txt 文件。

have fun.

常见问题
-------

1.自定义词典为什么没有生效？

请确保你的扩展词典的文本格式为 UTF8 编码

2.如何手动安装？


```bash
git clone https://github.com/medcl/elasticsearch-analysis-ik
cd elasticsearch-analysis-ik
git checkout tags/{version}
mvn clean
mvn compile
mvn package
```

拷贝和解压release下的文件: #{project_path}/elasticsearch-analysis-ik/target/releases/elasticsearch-analysis-ik-*.zip 到你的 elasticsearch 插件目录, 如: plugins/ik
重启elasticsearch

3.分词测试失败
请在某个索引下调用analyze接口测试,而不是直接调用analyze接口
如:
```bash
curl -XGET "http://localhost:9200/your_index/_analyze" -H 'Content-Type: application/json' -d'
{
   "text":"中华人民共和国MN","tokenizer": "my_ik"
}'
```


4. ik_max_word 和 ik_smart 什么区别?


ik_max_word: 会将文本做最细粒度的拆分，比如会将“中华人民共和国国歌”拆分为“中华人民共和国,中华人民,中华,华人,人民共和国,人民,人,民,共和国,共和,和,国国,国歌”，会穷尽各种可能的组合，适合 Term Query；

ik_smart: 会做最粗粒度的拆分，比如会将“中华人民共和国国歌”拆分为“中华人民共和国,国歌”，适合 Phrase 查询。

Changes
------
*自 v5.0.0 起*

- 移除名为 `ik` 的analyzer和tokenizer,请分别使用 `ik_smart` 和 `ik_max_word`


Thanks
------
YourKit supports IK Analysis for ElasticSearch project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
