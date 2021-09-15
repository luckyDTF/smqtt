package io.github.quickmsg.source;

import io.github.quickmsg.common.rule.source.Source;
import io.github.quickmsg.common.rule.source.SourceDefinition;
import io.github.quickmsg.common.rule.source.SourceBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author luxurong
 * @date 2021/8/25 15:50
 */
public class SourceManager {

   private static final Map<Source, SourceBean>  CACHE_BEANS = new ConcurrentHashMap<>();


   public static SourceBean getSourceBean(Source source){
       return CACHE_BEANS.get(source);
   }

   public static void loadSource(SourceDefinition sourceDefinition){
      SourceBean.SOURCE_BEAN_LIST.forEach(sourceBean -> {
         if (sourceBean.support(sourceDefinition.getSource())) {
            if (sourceBean.bootstrap(sourceDefinition.getSourceAttributes())) {
               CACHE_BEANS.put(sourceDefinition.getSource(), sourceBean);
            }
         }
      });
   }
}