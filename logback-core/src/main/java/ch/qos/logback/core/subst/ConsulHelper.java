package ch.qos.logback.core.subst;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson.JSON;
import ch.qos.logback.core.spi.PropertyContainer;

public class ConsulHelper {
	private static final String CONSUL_CUSTOM_KEY = "consulCustom";
	private static final String CONSUL_ENV_KEY = "consulEnv";
	private static final String CONSUL_KEY = "consul";
	
	private static final String CONSUL_CUSTOM_DEFAULT = "http://consul.zeasnapp.uk:8500/v1/kv/saas/${profileActive}/${appName}";
	private static final String CONSUL_ENV_DEFAULT = "http://consul.zeasnapp.uk:8500/v1/kv/saas/${profileActive}/common";
	private static final String CONSUL_DEFAULT = "http://consul.zeasnapp.uk:8500/v1/kv/saas/common";
	
	private static final String MAVEN_PROPERTY_FILE_NAME = "mavenProperties.properties";
	private static java.util.Properties mavenProperties;
	
	private static ConcurrentHashMap<String, CacheObj> remoteCache = new ConcurrentHashMap<>();
	
	private ConsulHelper() {}
	
	public static String getValue(String key) {
		return getValue(key, null, null);
	}
	
	public static String getValue(String key, PropertyContainer propertyContainer0, PropertyContainer propertyContainer1) {
		String[] keySplits = key.split("\\.");
		int len = keySplits.length;
		
		if(len < 2) {
			return null;
		}
		
		String urlKey = keySplits[0];
		List<String> jsonKeys = new ArrayList<>();
		
		for(int i=1; i<len; i++) {
			jsonKeys.add(keySplits[i]);
		}
		
		String url = lookupUrl(urlKey, propertyContainer0, propertyContainer1);
		if(url == null) {
			return null;
		}
		
		return getRemoveValue(url, jsonKeys);
	}
	
	private static String getRemoveValue(String url, List<String> jsonKeys) {
		CacheObj obj = remoteCache.computeIfAbsent(url, key -> {
			String queryValue = null;
			
			try {
				queryValue = getValueFromConsulUrl(url);
				
			} catch(NotHttp200Exception ex) {
				//configuration not exist in consul.
				
			}catch (Exception e) {
				e.printStackTrace();
			}
			
			return new CacheObj(queryValue);
		
		});
		
		return obj.getJsonValue(jsonKeys);
	}
	
	private static String lookupUrl(String key, PropertyContainer propertyContainer0, PropertyContainer propertyContainer1) {
		String value = null;
		
		if(propertyContainer0 != null) {
			value = propertyContainer0.getProperty(key);
	        if (value != null) {
	        	return value;
	        }
		}
        
        if (propertyContainer1 != null) {
            value = propertyContainer1.getProperty(key);
            if (value != null) {
            	return value;
            }   
        }
        
        value = lookupKeyInMaven(key);
        if (value != null) {
            return value;
        }

        return null;
    }
	
	private static String lookupKeyInMaven(String key) {
		if(CONSUL_CUSTOM_KEY.equals(key) || CONSUL_ENV_KEY.equals(key) || CONSUL_KEY.equals(key)) {
			String value = getMavenProperty(key);
			
			if(value == null) {
				if(CONSUL_CUSTOM_KEY.equals(key)) {
					value = CONSUL_CUSTOM_DEFAULT;
					
				}else if(CONSUL_ENV_KEY.equals(key)) {
					value = CONSUL_ENV_DEFAULT;
					
				}else{
					value = CONSUL_DEFAULT;
				}
			}
			
			String profileActive = getMavenProperty("profileActive");
			String appName = getMavenProperty("appName");
			
			if(profileActive != null) {
				value = value.replace("${profileActive}", profileActive).replace("@profileActive@", profileActive);
			}
			if(appName != null) {
				value = value.replace("${appName}", appName).replace("@appName@", appName);
			}
			
			return value;
			
		}else {
			return null;
		}
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
	
	private static String getMavenProperty(String key) {
		if(mavenProperties == null) {
			loadMavenProperties();
		}
		
		return mavenProperties.getProperty(key);
	}
	
	private static void loadMavenProperties() {
		mavenProperties = new Properties();
		
		InputStream input = null;
		try {
			input = ConsulHelper.class.getResourceAsStream("/" + MAVEN_PROPERTY_FILE_NAME);
			
			//MAVEN_PROPERTY_FILE_NAME not found. the 'properties-maven-plugin' plugin is not configured in pom.xml
			if(input == null) {
				return;
			}
			
			mavenProperties.load(input);
			
		} catch (Exception e) {
			//ignore exception
			
		} finally {
			try{
				if(input != null){
					input.close(); 
				}
	 		}catch(Exception ex){
	 			//ignore exception
	 		}
		}
	}
	
	static class CacheObj{
		private Map<String, Object> yaml;

		public CacheObj(String queryValue) {
			super();
			
			this.yaml = StringUtils.isNotEmpty(queryValue) ? new Yaml().load(queryValue) : new HashMap<>();
		}
		
		@SuppressWarnings("rawtypes")
		public String getJsonValue(List<String> jsonKeys) {
			if(CollectionUtils.isEmpty(jsonKeys) || MapUtils.isEmpty(this.yaml)) {
				return null;
			}
			
			Object tmp = null;
			Map<?, ?> tmpMap = this.yaml;
			
			for(String key : jsonKeys) {
				tmp = tmpMap.get(key);
				
				if(!(tmp instanceof Map)) {
					break;
					
				}else {
					tmpMap = (Map) tmp;
				}
			}
			
			return tmp == null || tmp instanceof Map ? null : tmp.toString();
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
