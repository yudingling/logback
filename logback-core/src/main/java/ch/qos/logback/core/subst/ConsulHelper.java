package ch.qos.logback.core.subst;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSON;

import ch.qos.logback.core.spi.PropertyContainer;
import ch.qos.logback.core.util.OptionHelper;

public class ConsulHelper {
	private static final String KEY_HOLDER = "{key}";
	private static ConcurrentHashMap<String, CacheObj> remoteCache = new ConcurrentHashMap<>();
	
	public static String getValue(String key, PropertyContainer propertyContainer0, PropertyContainer propertyContainer1) {
		String[] keySplits = key.split("\\.");
		int len = keySplits.length;
		
		if(len < 2) {
			return null;
		}
		
		String urlKey = keySplits[0];
		String paramKey = keySplits[1];
		List<String> jsonKeys = new ArrayList<>();
		
		for(int i=2; i<len; i++) {
			jsonKeys.add(keySplits[i]);
		}
		
		String url = lookupKey(urlKey, propertyContainer0, propertyContainer1);
		if(url == null) {
			return null;
		}
		
		return getRemoveValue(url, paramKey, jsonKeys);
	}
	
	private static String getRemoveValue(String url, String paramKey, List<String> jsonKeys) {
		String cacheKey = url + "_" + paramKey;
		
		CacheObj obj = remoteCache.computeIfAbsent(cacheKey, key -> {
			String queryUrl = combineQueryUrl(url, paramKey);
			String queryValue = null;
			
			try {
				queryValue = getValueFromConsulUrl(queryUrl);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return new CacheObj(queryValue);
		
		});
		
		return obj.getJsonValue(jsonKeys);
	}
	
	private static String combineQueryUrl(String url, String paramKey) {
		if(url.indexOf(KEY_HOLDER) >= 0) {
			return url.replace(KEY_HOLDER, paramKey);
			
		}else {
			return url.endsWith("/") ? (url + paramKey) : (url + "/" + paramKey);
		}
	}
	
	private static String lookupKey(String key, PropertyContainer propertyContainer0, PropertyContainer propertyContainer1) {
        String value = propertyContainer0.getProperty(key);
        if (value != null)
            return value;

        if (propertyContainer1 != null) {
            value = propertyContainer1.getProperty(key);
            if (value != null)
                return value;
        }

        value = OptionHelper.getSystemProperty(key, null);
        if (value != null)
            return value;

        value = OptionHelper.getEnv(key);
        if (value != null) {
            return value;
        }

        return null;
    }
	
	private static String getValueFromConsulUrl(String url) throws IOException, NotHttp200Exception{
		String txt = HttpClientUtil.httpGet(url);
		if(StringUtils.isNotEmpty(txt)){
			List<ConsulObj> objs = JSON.parseArray(txt, ConsulObj.class);
			
			if(CollectionUtils.isNotEmpty(objs)){
				return objs.get(0).getDecodedValue();
			}
		}
		
		return null;
	}
	
	static class CacheObj{
		private String queryValue;

		public CacheObj(String queryValue) {
			super();
			this.queryValue = queryValue;
		}
		
		@SuppressWarnings("rawtypes")
		public String getJsonValue(List<String> jsonKeys) {
			if(CollectionUtils.isEmpty(jsonKeys)) {
				return this.queryValue;
			}
			
			if(StringUtils.isEmpty(this.queryValue)) {
				return null;
			}
			
			Object tmp = JSON.parseObject(this.queryValue, Map.class);
			for(String key : jsonKeys) {
				if(tmp instanceof Map) {
					Map tmpMap = (Map) tmp;
					
					tmp = tmpMap.get(key);
				}
			}
			
			return tmp instanceof Map ? JSON.toJSONString(tmp) : tmp.toString();
		}
	}
	
	static class ConsulObj{
		private Integer LockIndex;
		private String Key;
		private Integer Flags;
		private String Value;
		private Long CreateIndex;
		private Long ModifyIndex;
		
		public Integer getLockIndex() {
			return LockIndex;
		}
		public void setLockIndex(Integer lockIndex) {
			LockIndex = lockIndex;
		}
		public String getKey() {
			return Key;
		}
		public void setKey(String key) {
			Key = key;
		}
		public Integer getFlags() {
			return Flags;
		}
		public void setFlags(Integer flags) {
			Flags = flags;
		}
		public String getValue() {
			return Value;
		}
		public void setValue(String value) {
			Value = value;
		}
		public Long getCreateIndex() {
			return CreateIndex;
		}
		public void setCreateIndex(Long createIndex) {
			CreateIndex = createIndex;
		}
		public Long getModifyIndex() {
			return ModifyIndex;
		}
		public void setModifyIndex(Long modifyIndex) {
			ModifyIndex = modifyIndex;
		}
		
		public String getDecodedValue(){
			if(StringUtils.isNotEmpty(this.Value)){
				byte[] data = Base64.getDecoder().decode(this.Value);
				return new String(data, StandardCharsets.UTF_8).trim();
				
			}else{
				return null;
			}
		}
	}
}
