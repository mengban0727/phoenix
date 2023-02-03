package thread;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * hashmap扩容时，查不到之前的数据，线程不安全
 */
public class HashMapNotSafe {

  //http://learn.lianglianglee.com/%E4%B8%93%E6%A0%8F/Java%20%E5%B9%B6%E5%8F%91%E7%BC%96%E7%A8%8B%2078%20%E8%AE%B2-%E5%AE%8C/29%20HashMap%20%E4%B8%BA%E4%BB%80%E4%B9%88%E6%98%AF%E7%BA%BF%E7%A8%8B%E4%B8%8D%E5%AE%89%E5%85%A8%E7%9A%84%EF%BC%9F.md
  public static void main(String[] args) {
    final Map<Integer, String> map = new HashMap<>();

    final Integer targetKey = 0b1111_1111_1111_1111; // 65 535

    final String targetValue = "v";

    map.put(targetKey, targetValue);

    new Thread(() -> {

      IntStream.range(0, targetKey).forEach(key -> map.put(key, "someValue"));

    }).start();

    while (true) {

      if (null == map.get(targetKey)) {

        throw new RuntimeException("HashMap is not thread safe.");

      }

    }

  }

}