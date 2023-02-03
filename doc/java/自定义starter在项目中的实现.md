## Background

亿欧不同的项目组负责的产品都有上传图片的功能，亿欧购买的是七牛云存储服务，把图片统一上传到七牛云上。这时，每个项目组都会自己开发上传图片的接口，代码都是参考官方文档进行开发，写来都差不多一样，显然重复造轮子并不推荐，因此自定义一个上传图片的starter，上传到maven私服，每个项目只需要在pom文件中引入，yml文件中开启自动配置就能够使用上传图片功能，简化了开发。

## 实现一个starter

### 上传图片的工具类

```java
@Slf4j
@NoArgsConstructor
@Setter
public class QiniuOperateSupport {

  private String accessKey;

  private String secretKey;

  private String bucketName;

  private String domainUrl;

  private String basePath;

  private String reportBucketName;

  private String reportDomainUrl;

  @Autowired
  private RestTemplate restTemplate;

  private Auth auth;

  @PostConstruct
  public void initAuth() {
    auth = Auth.create(accessKey, secretKey);
    bucketManager = new BucketManager(auth, c);
  }


  private Region region = Region.autoRegion();
  private Configuration c = new Configuration(region);

  /**
   * 创建上传对象
   */
  private UploadManager uploadManager = new UploadManager(c);

  private BucketManager bucketManager;

  public String uploadFile(MultipartFile pic) throws IOException {
    return uploadResource(pic, false, bucketName, domainUrl);
  }

  public QiniuFileMetaInfo getImageInfo(String qiniuImageUrl) {
    if (Objects.isNull(qiniuImageUrl) || qiniuImageUrl.isBlank()) {
      return null;
    }

    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(
        qiniuImageUrl + "?imageInfo");
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    HttpEntity<String> entity = new HttpEntity<>(httpHeaders);

    ResponseEntity<QiniuFileMetaInfo> responseEntity = restTemplate.exchange(
        uriComponentsBuilder.build().encode().toUri(), HttpMethod.GET, entity,
        QiniuFileMetaInfo.class);
    HttpStatus statusCode = responseEntity.getStatusCode();
    if (statusCode != HttpStatus.OK) {
      throw new ServiceException(ResultCode.INTERNAL_SERVER_ERROR,
          "Qiniu imageInfo service failed");
    }
    return responseEntity.getBody();
  }

  public String uploadReportFile(MultipartFile file) throws IOException {
    return uploadResource(file, true, reportBucketName, reportDomainUrl);
  }

  public String uploadReportLargeFile(MultipartFile file) throws IOException {
    return uploadResourceV2(file, reportBucketName, reportDomainUrl);
  }

  public void deleteReportFile(String fileKey) {
    deleteFile(reportBucketName, fileKey);
  }

  private String uploadResource(MultipartFile file, boolean keepOriginalFileName, String bucketName,
      String baseDomainUrl) throws IOException {
    try {
      byte[] uploadBytes = file.getBytes();
      ByteArrayInputStream byteInputStream = new ByteArrayInputStream(uploadBytes);
      String upToken = auth.uploadToken(bucketName);
      String baseUrl = basePath.isBlank() ? basePath : basePath + File.separator;
      String fileKey = keepOriginalFileName ? baseUrl + file.getOriginalFilename()
          : baseUrl + RandomHelper.getRandomString(20) + "." + FilenameUtils.getExtension(
              file.getOriginalFilename());
      Response response = uploadManager.put(byteInputStream, fileKey, upToken, null, null);
      // TODO: response not ok case handle

      //解析上传成功的结果
      DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
      log.info(putRet.key);
      log.info(putRet.hash);

      // TODO: optimize point, same content file upload case
      log.info("fileKey:{}", fileKey);
      return baseDomainUrl + fileKey;

    } catch (UnsupportedEncodingException | QiniuException e) {
      log.error(e.getMessage());
    }
    return null;
  }

  private String uploadResourceV2(MultipartFile file,
      String bucketName,
      String baseDomainUrl) throws IOException {

    String upToken = auth.uploadToken(bucketName);
    String baseUrl = basePath.isBlank() ? basePath : basePath + File.separator;
    String fileKey = baseUrl + file.getOriginalFilename();
    String localTempDir = Paths.get("/tmp", bucketName).toString();
    try {
      byte[] uploadBytes = file.getBytes();
      ByteArrayInputStream byteInputStream = new ByteArrayInputStream(uploadBytes);
      //设置断点续传文件进度保存目录
      FileRecorder fileRecorder = new FileRecorder(localTempDir);
      UploadManager multiPartUploadManager = new UploadManager(c, fileRecorder);
      Response response = multiPartUploadManager.put(byteInputStream, file.getSize(), fileKey, upToken,
          null, null, false);
      //解析上传成功的结果
      DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
      log.info(putRet.key);
      log.info(putRet.hash);

      // TODO: optimize point, same content file upload case
      log.info("fileKey:{}", fileKey);
      return baseDomainUrl + fileKey;
    } catch (UnsupportedEncodingException | QiniuException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void deleteFile(String bucketName, String fileKey) {
    try {
      bucketManager.delete(bucketName, fileKey);
    } catch (QiniuException e) {
      log.error(e.getMessage());
      // qiniu Error code
      // https://developer.qiniu.com/kodo/3928/error-responses
      throw new ServiceException(
          e.code() == 612 ? ResultCode.NOT_FOUND : ResultCode.INTERNAL_SERVER_ERROR,
          e.response.error);
    }
  }
}
```

### 自动配置类

当yaml中开启了common.sdk.qiniu.enable=true时，才会生效

```java
@EnableConfigurationProperties({QiniuProperties.class})
@ConditionalOnProperty(prefix = "common.sdk.qiniu", name = "enable", havingValue = "true")
public class QiniuAutoConfiguration {

  @Resource
  private QiniuProperties qiniuProperties;

  @Bean
  public QiniuOperateSupport qiniuOperateSupport() {
    QiniuOperateSupport qiniuOperateSupport = new QiniuOperateSupport();
    qiniuOperateSupport.setAccessKey(qiniuProperties.getAccessKey());
    qiniuOperateSupport.setSecretKey(qiniuProperties.getSecretKey());
    qiniuOperateSupport.setBucketName(qiniuProperties.getBucketName());
    qiniuOperateSupport.setPath(qiniuProperties.getPath());
    return qiniuOperateSupport;
  }
}
```



### 读取yml的配置文件

```java
@ConfigurationProperties(prefix = "common.sdk.qiniu")
public class QiniuProperties {

  private boolean enable = false;
  private String accessKey;
  private String secretKey;
  private String bucketName;
  private String domainUrl;
  private String basePath;

  private String reportBucketName;

  private String reportDomainUrl;

  public boolean isEnable() {
    return enable;
  }

  public void setEnable(boolean enable) {
    this.enable = enable;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }

  public String getDomainUrl() {
    return domainUrl;
  }

  public void setDomainUrl(String domainUrl) {
    this.domainUrl = domainUrl;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getReportBucketName() {
    return reportBucketName;
  }

  public void setReportBucketName(String reportBucketName) {
    this.reportBucketName = reportBucketName;
  }

  public String getReportDomainUrl() {
    return reportDomainUrl;
  }

  public void setReportDomainUrl(String reportDomainUrl) {
    this.reportDomainUrl = reportDomainUrl;
  }
}
```

### 配置spring.factories

```yam
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.iyiou.tp.staticfile.vendor.qiniu.QiniuAutoConfiguration
```



## SpringBoot自动配置原理

springboot启动类上的@SpringBootApplication注解包含@EnableAutoConfiguration注解，@EnableAutoConfiguration上个又包含了@Import注解，@Import注解能够将实例注入到spring容器中，springboot采用@Import注解的ImportSelector方式，定义了一个AutoConfigurationImportSelector类，这个类会去读取每个jar包下spring.factories文件中配置的类，将这些类进行条件过滤后注入到spring容器中。

### @SpringBootApplication

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public @interface SpringBootApplication 
```

### @EnableAutoConfiguration

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration 
```

### @Import

@Import将实例能够注入到spring的IOC容器中

### AutoConfigurationImportSelector

ImportSelector获取所有符合条件的类的全限定类名

### AutoConfigurationImportSelector#getAutoConfigurationEntry

```java
	protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
        //是否开启自动装配
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
        //exclude排除的类
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		
        //从所有依赖包的spring.factories文件加载类全限定名
        List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
		configurations = removeDuplicates(configurations);
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		checkExcludedClasses(configurations, exclusions);
		configurations.removeAll(exclusions);
        
        //根据@ConditionalOnXXX进行过滤，按需加载
		configurations = getConfigurationClassFilter().filter(configurations);
		fireAutoConfigurationImportEvents(configurations, exclusions);
		return new AutoConfigurationEntry(configurations, exclusions);
	}
```