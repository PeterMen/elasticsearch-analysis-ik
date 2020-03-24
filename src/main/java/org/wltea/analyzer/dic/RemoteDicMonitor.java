package org.wltea.analyzer.dic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.wltea.analyzer.help.ESPluginLoggerFactory;

public class RemoteDicMonitor implements Runnable {

	private static final Logger logger = ESPluginLoggerFactory.getLogger(RemoteDicMonitor.class.getName());

	private static CloseableHttpClient httpclient = HttpClients.createDefault();

	public static class RemoteDicFile extends DicFile{
		/** 上次更改时间 */
		private String last_modified;
		/** 资源属性 */
		private String eTags;

		public RemoteDicFile(String absolutePath) {
			super(absolutePath);
		}

		public String getLast_modified() {
			return last_modified;
		}

		public void setLast_modified(String last_modified) {
			this.last_modified = last_modified;
		}

		public String getETags() {
			return eTags;
		}

		public void setETags(String eTags) {
			this.eTags = eTags;
		}
	}

	/*
	 * 请求地址
	 */
	private ConcurrentLinkedQueue<RemoteDicFile> monitorFiles = new ConcurrentLinkedQueue<>();

	public void addFile(RemoteDicFile dicFile){
		boolean hasAdd = monitorFiles.stream().anyMatch(r -> r.getDicName().equals(dicFile.getDicName()));
		if(!hasAdd) {
			monitorFiles.offer(dicFile);
		}
	}

	public void run() {
		SpecialPermission.check();
		monitorFiles.forEach(dicFile -> {
			AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
				this.runUnprivileged(dicFile);
				return null;
			});
		});
	}

	/**
	 * 监控流程：
	 *  ①向词库服务器发送Head请求
	 *  ②从响应中获取Last-Modify、ETags字段值，判断是否变化
	 *  ③如果未变化，休眠1min，返回第①步
	 * 	④如果有变化，重新加载词典
	 *  ⑤休眠1min，返回第①步
	 */

	public void runUnprivileged(RemoteDicFile dicFile) {

		//超时设置
		RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10*1000)
				.setConnectTimeout(10*1000).setSocketTimeout(15*1000).build();

		HttpHead httpHead = new HttpHead(dicFile.getDicPath());
		httpHead.setConfig(rc);

		//设置请求头
		if (dicFile.getLast_modified() != null) {
			httpHead.setHeader("If-Modified-Since", dicFile.getLast_modified());
		}
		if (dicFile.getETags() != null) {
			httpHead.setHeader("If-None-Match", dicFile.getETags());
		}

		CloseableHttpResponse response = null;
		try {

			response = httpclient.execute(httpHead);

			//返回200 才做操作
			if(response.getStatusLine().getStatusCode()==200){

				if (((response.getLastHeader("Last-Modified")!=null) && !response.getLastHeader("Last-Modified").getValue().equalsIgnoreCase(dicFile.getLast_modified()))
						||((response.getLastHeader("ETag")!=null) && !response.getLastHeader("ETag").getValue().equalsIgnoreCase(dicFile.eTags))) {

					// 远程词库有更新,需要重新加载词典，并修改last_modified,eTags
					List<String> words = getRemoteWords(dicFile.getDicPath());
					Dictionary.getSingleton().addWords(dicFile.getDicName(), words);
					dicFile.setLast_modified(response.getLastHeader("Last-Modified")==null?null:response.getLastHeader("Last-Modified").getValue());
					dicFile.setETags(response.getLastHeader("ETag")==null?null:response.getLastHeader("ETag").getValue());
				}
			}else if (response.getStatusLine().getStatusCode()==304) {
				//没有修改，不做操作
				//noop
			}else{
				logger.info("remote_ext_dict {} return bad code {}" , dicFile.getDicPath() , response.getStatusLine().getStatusCode() );
			}

		} catch (Exception e) {
			logger.error("remote_ext_dict {} error!",e , dicFile.getDicPath());
		}finally{
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	public static DictSegment loadRemoteDic(DicFile dicFile){
		logger.info("[Dict Loading] " + dicFile.getDicPath());
		DictSegment dictSegment = new DictSegment((char) 0);
		List<String> lists = getRemoteWords(dicFile.getDicPath());
		// 如果找不到扩展的字典，则忽略
		if (lists == null) {
			logger.error("[Dict Loading] " + dicFile.getDicPath() + "加载失败");
			return dictSegment;
		}
		for (String theWord : lists) {
			if (theWord != null && !"".equals(theWord.trim())) {
				logger.info(theWord);
				dictSegment.fillSegment(theWord.trim().toLowerCase().toCharArray());
			}
		}
		return dictSegment;
	}

	private static List<String> getRemoteWords(String location) {
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<List<String>>) () -> {
			return getRemoteWordsUnprivileged(location);
		});
	}


	/**
	 * 从远程服务器上下载自定义词条
	 */
	public static List<String> getRemoteWordsUnprivileged(String location) {

		List<String> buffer = new ArrayList<String>();
		RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10 * 1000).setConnectTimeout(10 * 1000)
				.setSocketTimeout(60 * 1000).build();
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response;
		BufferedReader in;
		HttpGet get = new HttpGet(location);
		get.setConfig(rc);
		try {
			response = httpclient.execute(get);
			if (response.getStatusLine().getStatusCode() == 200) {

				String charset = "UTF-8";
				// 获取编码，默认为utf-8
				HttpEntity entity = response.getEntity();
				if(entity!=null){
					Header contentType = entity.getContentType();
					if(contentType!=null&&contentType.getValue()!=null){
						String typeValue = contentType.getValue();
						if(typeValue!=null&&typeValue.contains("charset=")){
							charset = typeValue.substring(typeValue.lastIndexOf("=") + 1);
						}
					}

					if (entity.getContentLength() > 0) {
						in = new BufferedReader(new InputStreamReader(entity.getContent(), charset));
						String line;
						while ((line = in.readLine()) != null) {
							buffer.add(line);
						}
						in.close();
						response.close();
						return buffer;
					}
				}
			}
			response.close();
		} catch (IllegalStateException | IOException e) {
			logger.error("getRemoteWords {} error", e, location);
		}
		return buffer;
	}
}
