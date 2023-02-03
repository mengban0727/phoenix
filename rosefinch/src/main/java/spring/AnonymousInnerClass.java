package spring;

import java.util.HashMap;
import java.util.Map;

/**
 * spring 三级缓存原理
 *
 * @author zhangjie
 */
public class AnonymousInnerClass {

  public Map<String, ObjectFactory<?>> tmpMap = new HashMap<>();

  public String method1(String beanName) {
    tmpMap.put(beanName, new ObjectFactory<String>() {
      @Override
      public String getObject() {
        return AnonymousInnerClass.this.method2(beanName);
      }
    });
    return beanName;
  }

  public String method2(String name) {
    name = name + "123";
    System.out.println(name);
    return name;
  }

  public static void main(String[] args) {
    AnonymousInnerClass anonymousInnerClass = new AnonymousInnerClass();
    String name = "bean";
    anonymousInnerClass.method1(name);
    anonymousInnerClass.tmpMap.get(name).getObject();
  }


}
