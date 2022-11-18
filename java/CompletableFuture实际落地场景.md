# 1. 异步获取结果

把CompletableFuture当作Future使用，与FutureTask作用相同

## 业务背景

亿欧后台内容的生产主要分人工创作和程序自动生成，其中人工创作编写内容入库到mysql，同时会写入es提供检索功能；程序生成，主要是爬虫抓取，以及按照一定规则来生产内容，程序生成的内容也写入到mysql中，并没有写入到es中，因此需要将这部分进行同步。

## 数据同步方案

将mysql中的数据同步到mq中，消费mq的消息，构造完整数据后写入es

## 数据增量同步工具选型

mysql是使用的阿里云的rds，可以选择阿里云的数据订阅产品，但考虑成本问题，选择其他工具。主要选型了两款数据增量同步工具，canal和maxwell，因为阿里云的rds有自己的binlog清理规则，maxwell会找不到binlog文件而导致不能继续同步的问题，其他部门有使用maxwell工具，他们的解决方法是清空maxwell的binlog相关元数据信息，但会存在丢数据的风险，而canal很好的支持了阿里云的rds，因此使用canal作为同步工具。

## canal改造

由于mq使用的是阿里云的RocketMQ，需要对canal进行二次开发，适配阿里云RocketMQ，canal使用的版本是1.1.4，开启flatMessage模式（后期可升级到性能更好的1.1.5版本），需要保证binlog的消息顺序性，使用路由选择是，多topic单分区的顺序消息，单张表的数据写入一个topic的单分区中。采用dockerfile部署方式，部署到k8s集群环境中

### canal性能主要优化参数

```properties
canal.instance.memory.rawEntry = true (表示是否需要提前做序列化，非flatMessage场景需要设置为true)
canal.mq.flatMessage = false (false代表二进制协议，true代表使用json格式，二进制协议有更好的性能)
canal.mq.dynamicTopic (动态topic配置定义，可以针对不同表设置不同的topic，在flatMessage模式下可以提升并行效率)
canal.mq.partitionsNum/canal.mq.partitionHash (分区配置，对写入性能有反作用，不过可以提升消费端的吞吐)
```

### 自定义CanalAliRocketMQProducer

```java
public class CanalAliRocketMQProducer implements CanalMQProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanalAliRocketMQProducer.class);
    private OrderProducer producer;
    private MQProperties mqProperties;
    
    //对表中的字段进行过滤，只同步需要的字段
    private Map<String, List<String>> fieldFilterMap;
    @Override
    public void init(MQProperties mqProperties) {
        this.mqProperties = mqProperties;
        LOGGER.info("flatMessage:{}", mqProperties.getFlatMessage());

        String fieldFilter = mqProperties.getFieldFilter();
        if (StringUtils.isNotEmpty(fieldFilter)) {
            String[] filters = fieldFilter.split(";");
            fieldFilterMap = new HashMap<>(filters.length);
            for (String filter : filters) {
                String[] tableFilters = filter.split(":");
                String tableName = tableFilters[0];
                String[] tableFields = tableFilters[1].split(",");
                List<String> fieldList = Arrays.stream(tableFields).collect(Collectors.toList());
                fieldFilterMap.put(tableName, fieldList);
            }
        }

        Properties properties = new Properties();
        // 您在消息队列RocketMQ版控制台创建的Group ID。
        properties.put(PropertyKeyConst.GROUP_ID, mqProperties.getProducerGroup());
        // AccessKey ID阿里云身份验证，在阿里云RAM控制台创建。
        properties.put(PropertyKeyConst.AccessKey, mqProperties.getAliyunAccessKey());
        // AccessKey Secret阿里云身份验证，在阿里云RAM控制台创建。
        properties.put(PropertyKeyConst.SecretKey, mqProperties.getAliyunSecretKey());
        // 设置TCP接入域名，进入消息队列RocketMQ版控制台实例详情页面的接入点区域查看。
        properties.put(PropertyKeyConst.NAMESRV_ADDR, mqProperties.getServers());
        producer = ONSFactory.createOrderProducer(properties);
        producer.start();

    }

    @Override
    public void send(CanalDestination destination, Message message, Callback callback) {
        try {
            if (!StringUtils.isEmpty(destination.getDynamicTopic())) {
                // 动态topic
                Map<String, com.alibaba.otter.canal.protocol.Message> messageMap = MQMessageUtils
                    .messageTopics(message,
                                   destination.getTopic(),
                                   destination.getDynamicTopic());
                for (Map.Entry<String, com.alibaba.otter.canal.protocol.Message> entry : messageMap
                     .entrySet()) {
                    String topicName = entry.getKey().replace('.', '_');
                    if (StringUtils.isNotEmpty(topicName)) {
                        com.alibaba.otter.canal.protocol.Message messageSub = entry.getValue();
                        send(topicName, messageSub);
                    }
                }
            }

            callback.commit();
        } catch (Exception e) {
            LOGGER.error("发送消息失败，", e);
            callback.rollback();
        }

    }
    
    public void send(String topicName, com.alibaba.otter.canal.protocol.Message data) {
        if (!mqProperties.getFlatMessage()) {
            com.aliyun.openservices.ons.api.Message msg = new com.aliyun.openservices.ons.api.Message(
                topicName,
                "",
                CanalMessageSerializer.serializer(data, true)
            );
            sendMessage(msg);
        } else {
            List<FlatMessage> flatMessages = MQMessageUtils.messageConverter(data);
            if (flatMessages != null) {
                for (FlatMessage flatMessage : flatMessages) {
                    if (fieldFilterMap.containsKey(flatMessage.getTable())) {
                        List<Map<String, String>> oldDataList = flatMessage.getData();
                        List<Map<String, String>> dataList = new ArrayList<>();
                        List<String> fieldList = fieldFilterMap.get(flatMessage.getTable());
                        oldDataList.forEach(oldData -> {
                            Map<String, String> newData = new HashMap<>(fieldList.size());
                            fieldList.forEach(field -> newData.put(field, oldData.get(field)));
                            dataList.add(newData);
                        });
                        flatMessage.setData(dataList);
                        flatMessage.setSqlType(null);
                        flatMessage.setMysqlType(null);
                        flatMessage.setOld(null);
                    }

                    try {
                        com.aliyun.openservices.ons.api.Message msg = new com.aliyun.openservices.ons.api.Message(
                            topicName,
                            "",
                            JSON.toJSONString(flatMessage, SerializerFeature.WriteMapNullValue).getBytes()
                        );
                        sendMessage(msg);
                    } catch (Exception e) {
                        LOGGER.error("send flat message error", e);
                        throw e;
                    }
                }
            }
        }
    }

    private void sendMessage(com.aliyun.openservices.ons.api.Message msg) {
        String shardingKey = "0";
        try {
            producer.send(msg, shardingKey);
        } catch (Exception e) {
            // 消息发送失败，需要进行重试处理，可重新发送这条消息或持久化这条数据进行补偿处理。
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void stop() {

    }
}
```

## 消费程序

### RocketMQ SDK

由于是内网环境，批量push模式消费只支持TCP连接方式

```xml
    <dependency>
      <groupId>com.aliyun.openservices</groupId>
      <artifactId>ons-client</artifactId>
      <!--以下版本号请替换为Java SDK的最新版本号-->
      <version>1.8.8.5.Final</version>
    </dependency>
```

### 批量消费

* 接收到批量binlog消息，交给多个线程去执行，需要考虑binlog的顺序性，同一条数据增删改的binlog需要分发到相同的线程去执行，根据binlog的id进行取模路由，为了让每个线程固定去处理id余数相同的binlog，也就是说所有id余数为1的binlog都只交给线程1来处理，需要自定义线程

* 每个线程维护一个ArrayBlockingQueue队列，线程循环从队列中获取任务进行执行，因此，将余数相同的一批binlog消息封装成任务提交到队列中去，返回CompletableFuture对象，可以异步从CompletableFuture对象中获取任务执行结果，主线程等待所有CompletableFuture完成后，提交批量消费的offset。



### binlog消息封装

```java
public class BinlogMessage {

    private int id;
    /**
   * 文章类型
   */
    private int type;

    /**
   * 发布时间
   */
    private String postTime;

    /**
   * 发布状态
   */
    private int status;

    /**
   * binlog类型
   */
    private String binlogType;
}
```

### 阻塞队列中的任务

```java
public class TaskWrapper {
    //任务异步返回的future
    private final CompletableFuture<WorkResult> future;
	
    //id取模相同的binlog消息
    private final List<BinlogMessage> taskList;

    public TaskWrapper(List<BinlogMessage> taskList) {
        this.future = new CompletableFuture<>();
        this.taskList = taskList;
    }

	//任务执行完成时调用
    public void complete(WorkResult workResult) {
        future.complete(workResult);
    }

    public void completeExceptionally(Exception e) {
        getFuture().completeExceptionally(e);
    }
}
```

### 收到binlog消息后分发

```java
public class BatchMessageListener implements BatchMessageListener {
    private final List<BinlogMessageQueue> queues;
    @Override
    public Action consume(List<Message> list, ConsumeContext consumeContext) {
        long startTime = System.currentTimeMillis();
        log.info("收到消息{}条", list.size());
        handleMessageBatch(list);
        long endTime = System.currentTimeMillis();
        long seconds = (endTime - startTime) / 1000;
        long perSecond = seconds == 0 ? list.size() : list.size() / seconds;
        log.info("处理[{}]条消息,耗时[{}]s,平均每秒处理[{}]条", list.size(), seconds, perSecond);
        return Action.CommitMessage;
    }

    //binlog按照id取模分发到固定线程
    private void handleMessageBatch(List<Message> messages) {
        List<CompletableFuture<WorkResult>> futureList = new ArrayList<>();
        Map<Integer, List<BinlogMessage>> taskMap = new HashMap<>(16);

        for (Message message : messages) {
            String messageBodyString;
            try {
                messageBodyString = new String(message.getBody(), Constants.DEFAULT_CHARSET);
            } catch (UnsupportedEncodingException e) {
                log.error(e.toString());
                throw new ServiceException("转换异常");
            }
            //消息转换为binlog
            CanalBinlog<CanalArticleData> postBinlog = JSON
                .parseObject(messageBodyString, new TypeReference<>() {
                });
            assert postBinlog != null;
            if (postBinlog.getData() == null) {
                continue;
            }
            List<CanalArticleData> postBinlogData = postBinlog.getData();
            for (CanalArticleData data : postBinlogData) {
                int articleId = Integer.parseInt(data.getId());
                //根据id取模
                int queueIndex = articleId % queues.size();
                if (!taskMap.containsKey(queueIndex)) {
                    taskMap.put(queueIndex, new ArrayList<>());
                }
                List<BinlogMessage> binlogMessages = taskMap.get(queueIndex);
                BinlogMessage binlogMessage = BinlogMessage.builder()
                    .id(articleId)
                    .postTime(data.getPostTime())
                    .type(Integer.parseInt(data.getType()))
                    .status(Integer.parseInt(data.getDataStatus()))
                    .binlogType(postBinlog.getType())
                    .build();
                binlogMessages.add(binlogMessage);
            }
        }
        taskMap.forEach((queueIndex, binlogList) -> {
            CompletableFuture<WorkResult> future = queues.get(queueIndex)
                .submit(binlogList);
            futureList.add(future);
        });
        log.info("等待{}条消息消费", messages.size());
        CompletableFuture
            .allOf(futureList.toArray(new CompletableFuture[0])).join();
        log.info("{}条消息处理完成", messages.size());
    }
}
```



### 自定义线程BinlogMessageQueue

```java
public class BinlogMessageQueue extends Thread {

  //处理binlog消息的类，不用在意
  private MessageHandler messageHandler;

  //存放任务的队列
  private final ArrayBlockingQueue<TaskWrapper> workQueue =
      new ArrayBlockingQueue<>(1);

  //提交任务后返回一个future
  public CompletableFuture<WorkResult> submit(List<BinlogMessage> taskList) {
    TaskWrapper taskWrapper = new TaskWrapper(
        taskList);
    try {
      workQueue.put(taskWrapper);
      return taskWrapper.getFuture();
    } catch (InterruptedException e) {
      log.error(e.toString());
      throw new RuntimeException(e);
    }
  }

  /**
   1. 循环从队列中获取任务执行 workQueue.take()
   2. 拿到binlog消息后按照删除、新增或者更新来进行分类
   3. 分类后处理数据 messageHandler.handleMessageBatch
   4. 最后调用complte完成任务，结束get等待
  */
  @Override
  public void run() {
    for (; ; ) {
      try {
        TaskWrapper taskWrapper = workQueue.take();
        try {
          //task按照delete分组
          List<List<BinlogMessage>> splitList = new ArrayList<>();
          List<BinlogMessage> taskList = taskWrapper.getTaskList();

          boolean preDeleteFlag = false;
          List<BinlogMessage> preList = new ArrayList<>();
          splitList.add(preList);

          for (BinlogMessage task : taskList) {
            //如果是删除
            if (CanalBinlogType.DELETE.getType().equals(task.getBinlogType())) {
              if (preDeleteFlag) {
                preList.add(task);
              } else {
                preList = new ArrayList<>();
                splitList.add(preList);
                preList.add(task);
                preDeleteFlag = true;
              }
            }
            //新增/更新
            else {
              if (preDeleteFlag) {
                preList = new ArrayList<>();
                splitList.add(preList);
                preList.add(task);
                preDeleteFlag = false;
              } else {
                preList.add(task);
              }

            }
          }

          for (List<BinlogMessage> list : splitList) {
            if (!list.isEmpty()) {
              messageHandler.handleMessageBatch(list);
            }
          }
          taskWrapper.complete(new WorkResult(true));
        } catch (Exception e) {
          log.error("批量消费失败", e);
          taskWrapper.completeExceptionally(e);
        }
      } catch (InterruptedException e) {
        log.error(e.toString());
      }
    }
  }
}
```



# 2.远程调用串行改成并行

## 业务背景

亿欧网查询文章详情页时，需要查询快讯，报告，多种类型的文章，每种查询都需要远程调用，从mysql或者redis中查询数据，原来是串行调用，一个一个去查，接口响应时间长，更改为并行调用后，总耗时时长由最长的那个查询决定，性能提升几倍。

## 优化后代码

```java
String finalDomain = domain;
//10篇快讯
CompletableFuture<List<CrmNewsFlashVO>> briefingFuture = CompletableFuture
    .supplyAsync(() -> {
        try {
            return newsService.listFieldNews(tagList, 10, finalDomain);
        } catch (Exception e) {
            log.error(e);
        }
        return Collections.emptyList();
    }, taskExecutor);

//5篇报告
CompletableFuture<List<CrmReportVO>> reportFuture = CompletableFuture.supplyAsync(() -> {
    try {
        return reportService.listFieldReportList(tagList, tagIds, finalDomain);
    } catch (ParseException e) {
        log.error(e);
    }
    return Collections.emptyList();
}, taskExecutor);

//2篇深度type为4, 5
CompletableFuture<List<CrmPostVO>> depthFuture = CompletableFuture.supplyAsync(() -> {
    try {
        return postService.listFieldDepthPosts(tagList, tagIds, finalDomain);
    } catch (ParseException e) {
        log.error(e);
    }
    return Collections.emptyList();
}, taskExecutor);

//4篇长内容type为3, 4, 5
CompletableFuture<List<CrmPostVO>> analysisFuture = depthFuture.thenApply((depthResult) -> {
    List<Long> dupIds = depthResult.stream().map(CrmPostVO::getId)
        .collect(Collectors.toList());
    try {
        return postService.listFieldAnalysisPosts(tagList, tagIds, finalDomain, dupIds);
    } catch (ParseException e) {
        log.error(e);
    }
    return Collections.emptyList();
});

//4篇长内容type为1, 2
CompletableFuture<List<CrmPostVO>> newsFuture = CompletableFuture.supplyAsync(() -> {
    try {
        return postService
            .listFieldNewsPosts(tagList, tagIds, finalDomain);
    } catch (ParseException e) {
        log.error(e);
    }
    return Collections.emptyList();
}, taskExecutor);

CompletableFuture
    .allOf(briefingFuture, reportFuture, depthFuture, analysisFuture,newsFuture).join();

List<CrmReportVO> reportList = reportFuture.get();
List<CrmNewsFlashVO> briefingList = briefingFuture.get();
List<CrmPostVO> depthList = depthFuture.get();
List<CrmPostVO> analysisList = analysisFuture.get();
List<CrmPostVO> newsList = newsFuture.get();

//组装返回格式
FieldRecommend fieldRecommend = FieldRecommend.builder()
    .briefingList(briefingList)
    .depthList(depthList)
    .build();

FieldResponse fieldResponse = FieldResponse.builder()
    .recommend(fieldRecommend)
    .analysisList(analysisList)
    .newsList(newsList)
    .build();
if (reportList.size() > 0) {
    fieldRecommend.setReport(reportList.get(0));
}else{
    fieldRecommend.setReport(new CrmReportVO());
}
if (reportList.size() > 1) {
    fieldResponse.setResearchList(reportList.subList(1, reportList.size()));
}
return CommonResult.success(fieldResponse);
```







