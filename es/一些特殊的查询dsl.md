## 根据keyword前缀聚合

### 方案1-查询时script

```java
GET dp_intelligence_query/_search
{
  "size": 0,
  "query": {
    "bool": {
      "must_not": [
        {
          "term": {
            "attach_link_source.keyword": {
              "value": ""
            }
          }
        }
      ],
      "must": [
        {
          "exists": {
            "field": "attach_link_source.keyword"
          }
        }
      ]
    }
  },
  "aggs": {
    "link_pre": {
      "terms": {
        "script": """
        String fullUrl=doc['attach_link_source.keyword'].value;
        int lastIndex=fullUrl.lastIndexOf('/');
        lastIndex=lastIndex<0?fullUrl.length():lastIndex;
        return fullUrl.substring(0,lastIndex);
      """,
        "size": 10,
        "order": {
          "_count": "desc"
        }
      }
    }
  }
}
```

### 方案2-runtime fields

[铭毅天下-runtime fields](https://blog.csdn.net/laoyang360/article/details/120574142)

[Elasticsearch-runtime fields](https://www.elastic.co/guide/en/elasticsearch/reference/current/scripting-field-extraction.html)



## keyword 存储浮点数/整数聚合

keyword设置index：false

```java
POST test_index/_search?size=0
{
   "query": {
      "match_all": {}
   },
   "aggs": {
      "terms_aggs": {
         "max": {
            "script" : "new BigDecimal(doc['id'].value).floatValue()"
         }
      }
   }
}
```

