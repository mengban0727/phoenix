## 应用1

亿欧后台采用k8s部署应用在阿里云上，需要执行一些任务时，使用k8s中的job方式，在控制台手动创建pod任务来执行，执行完任务后释放出资源。不同的任务逻辑写在同一个应用中，根据应用启动时传递的命令参数不同来执行对应的任务，使用策略模式

## 策略comand接口

```java
public interface StrategyCommand {

   /**
   * 遵循Unix约定，如果命令执行正常，则返回0；否则为非0。
   * @param args 传入的参数
   * @return 成功标识
   */
    int execute(String... args);
}
```

## 策略ArticleStrategyCommand实现

```java
@Component("loadArticle")
@Slf4j
public class ArticleStrategyCommand implements Command {

  @Resource
  private ArticleService articleService;

  @Override
  public int execute(String... args) {
    try {
      articleService.loadArticleToEs();
      return 0;
    } catch (Exception e) {
      log.error("文章同步失败", e);
      return -1;
    }
  }
}
```

## 策略ReportStrategyCommand实现

```java
@Component("loadReport")
@Slf4j
public class ReportStrategyCommand implements Command {

  @Resource
  private ReportService reportService;

  @Override
  public int execute(String... args) {
    try {
      reportService.loadReportToEs();
      return 0;
    } catch (Exception e) {
      log.error("报告同步失败", e);
      return -1;
    }
  }
}
```

## 策略TopicStrategyCommand实现

```java
@Component("loadTopic")
@Slf4j
public class TopicStrategyCommand implements Command {

  @Resource
  private TopicService topicService;

  @Override
  public int execute(String... args) {
    try {
      topicService.loadTopicToEs();
      return 0;
    } catch (Exception e) {
      log.error("专题同步失败", e);
      return -1;
    }
  }
}
```

## 策略实现类注入到Map

有多种方式，从spring容器中拿到所有的策略实现类，根据不同的命令传参，执行不同的任务。

1. 直接@Resource或者@Autowired注解进行依赖注入
2. 利用容器感知ApplicationContextAware接口拿到applicationContext从容器中获取

```java
@Resource
private  Map<String, Command> commandMap;

@Override
public void run(String... args) {
    if (args.length == 0) {
        log.error("command not found");
        System.exit(-1);
    }

    if (!commandMap.containsKey(args[0])) {
        log.error("'{}' command not found", args[0]);
        System.exit(-1);
    }
    Command command = commandMap.get(args[0]);
    String[] arguments = Arrays.copyOfRange(args, 1, args.length);
    System.exit(command.execute(arguments));
}
```



## 应用2



## 参考

- [应用设计模式优化代码](https://www.modb.pro/db/425893)