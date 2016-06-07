package com.ge.predix.solsvc.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ge.predix.solsvc.api.InquiryDataAPI;
import com.ge.predix.solsvc.restclient.impl.CxfAwareRestClient;
import com.ge.predix.solsvc.spi.IServiceManagerService;
import com.ge.predix.solsvc.timeseries.bootstrap.config.TimeseriesRestConfig;
import com.ge.predix.solsvc.timeseries.bootstrap.config.TimeseriesWSConfig;
import com.ge.predix.solsvc.timeseries.bootstrap.factories.TimeseriesFactory;
import com.ge.predix.solsvc.timeseries.bootstrap.websocket.client.TimeseriesWebsocketClient;
import com.ge.predix.timeseries.entity.datapoints.ingestionrequest.Body;
import com.ge.predix.timeseries.entity.datapoints.ingestionrequest.DatapointsIngestion;
import com.ge.predix.timeseries.entity.datapoints.queryrequest.Aggregation;
import com.ge.predix.timeseries.entity.datapoints.queryrequest.DatapointsQuery;
import com.ge.predix.timeseries.entity.datapoints.queryrequest.latest.DatapointsLatestQuery;
import com.ge.predix.timeseries.entity.datapoints.queryresponse.DatapointsResponse;

/**
 * 
 * @author predix -
 */
@Component
public class InquiryDataImpl implements InquiryDataAPI {

	@Autowired
	private IServiceManagerService serviceManagerService;

	@Autowired
	private TimeseriesRestConfig timeseriesRestConfig;

	@Autowired
	private CxfAwareRestClient restClient;

	@Autowired
	private TimeseriesWSConfig tsInjectionWSConfig;

	@Autowired
	private TimeseriesWebsocketClient timeseriesWebsocketClient;

	@Autowired
	private TimeseriesFactory timeseriesFactory;

	private static Logger log = LoggerFactory.getLogger(InquiryDataImpl.class);

	/**
	 * -
	 */
	public InquiryDataImpl() {
		super();
	}

	/**
	 * -
	 */
	@PostConstruct
	public void init() {
		this.serviceManagerService.createRestWebService(this, null);
	}

	@Override
	public Response greetings() {
		return handleResult("Greetings from CXF Bean Rest Service " + new Date()); //$NON-NLS-1$
	}

	@Override
	public Response getYearlyPm25DataPoints(String id, String authorization, String starttime, String taglimit,
			String tagorder) {
		if (id == null) {
			return null;
		}

		List<Header> headers = generateHeaders();

		DatapointsQuery dpQuery = buildDatapointsQueryRequest(id, starttime, getInteger(taglimit), tagorder);
		DatapointsResponse response = this.timeseriesFactory.queryForDatapoints(this.timeseriesRestConfig.getBaseUrl(),
				dpQuery, headers);
		log.debug(response.toString());

		return handleResult(response);
	}

	@Override
	public Response getWeeklyPm25DataPoints(String id, String authorization, String starttime, String taglimit,
			String tagorder) {
		if (id == null) {
			return null;
		}
	
		List<Header> headers = generateHeaders();
	
		DatapointsQuery dpQuery = buildDatapointsQueryRequestWithAggregation(id, starttime, getInteger(taglimit), tagorder);
		DatapointsResponse response = this.timeseriesFactory.queryForDatapoints(this.timeseriesRestConfig.getBaseUrl(),
				dpQuery, headers);
		log.debug(response.toString());
	
		return handleResult(response);
	}

	/**
	 * 
	 * @param s
	 *            -
	 * @return
	 */
	private int getInteger(String s) {
		int inValue = 25;
		try {
			inValue = Integer.parseInt(s);

		} catch (NumberFormatException ex) {
			// s is not an integer
		}
		return inValue;
	}

	@Override
	public Response getLatestPm25DataPoints(String id, String authorization) {
		if (id == null) {
			return null;
		}
		List<Header> headers = generateHeaders();

		DatapointsLatestQuery dpQuery = buildLatestDatapointsQueryRequest(id);
		DatapointsResponse response = this.timeseriesFactory
				.queryForLatestDatapoint(this.timeseriesRestConfig.getBaseUrl(), dpQuery, headers);
		log.debug(response.toString());

		return handleResult(response);
	}

	@SuppressWarnings({ "unqualified-field-access", "nls" })
	private List<Header> generateHeaders() {
		List<Header> headers = this.restClient.getSecureTokenForClientId();
		this.restClient.addZoneToHeaders(headers, this.timeseriesRestConfig.getZoneId());
		return headers;
	}

	private DatapointsLatestQuery buildLatestDatapointsQueryRequest(String id) {
		DatapointsLatestQuery datapointsLatestQuery = new DatapointsLatestQuery();

		com.ge.predix.timeseries.entity.datapoints.queryrequest.latest.Tag tag = new com.ge.predix.timeseries.entity.datapoints.queryrequest.latest.Tag();
		tag.setName(id);
		List<com.ge.predix.timeseries.entity.datapoints.queryrequest.latest.Tag> tags = new ArrayList<com.ge.predix.timeseries.entity.datapoints.queryrequest.latest.Tag>();
		tags.add(tag);
		datapointsLatestQuery.setTags(tags);
		return datapointsLatestQuery;
	}

	/**
	 * 
	 * @param id
	 * @param startDuration
	 * @param tagorder
	 * @return
	 */
	private DatapointsQuery buildDatapointsQueryRequest(String id, String startDuration, int taglimit,
			String tagorder) {
		DatapointsQuery datapointsQuery = new DatapointsQuery();
		List<com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag> tags = new ArrayList<com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag>();
		datapointsQuery.setStart(startDuration);
		// datapointsQuery.setStart("1y-ago"); //$NON-NLS-1$
		String[] tagArray = id.split(","); //$NON-NLS-1$
		List<String> entryTags = Arrays.asList(tagArray);
		List<Aggregation> aggregations = new ArrayList<Aggregation>();
		for (String entryTag : entryTags) {
			com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag tag = new com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag();
			tag.setName(entryTag);
			tag.setLimit(taglimit);
			tag.setOrder(tagorder);
			tags.add(tag);
		}
		datapointsQuery.setTags(tags);
		return datapointsQuery;
	}
	/**
	 * 
	 * @param id
	 * @param startDuration
	 * @param tagorder
	 * @return
	 */
	private DatapointsQuery buildDatapointsQueryRequestWithAggregation(String id, String startDuration, int taglimit,
			String tagorder) {
		DatapointsQuery datapointsQuery = new DatapointsQuery();
		List<com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag> tags = new ArrayList<com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag>();
		datapointsQuery.setStart(startDuration);
		// datapointsQuery.setStart("1y-ago"); //$NON-NLS-1$
		String[] tagArray = id.split(","); //$NON-NLS-1$
		List<String> entryTags = Arrays.asList(tagArray);
		Aggregation aggregation = new Aggregation();
		aggregation.setType("avg");
		aggregation.setInterval("1d");
		List<Aggregation> aggregations = new ArrayList<Aggregation>();
		aggregations.add(aggregation);
		for (String entryTag : entryTags) {
			com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag tag = new com.ge.predix.timeseries.entity.datapoints.queryrequest.Tag();
			tag.setName(entryTag);
			tag.setLimit(taglimit);
			tag.setOrder(tagorder);
			tag.setAggregations(aggregations);
			tags.add(tag);
		}
		datapointsQuery.setTags(tags);
		return datapointsQuery;
	}

	@SuppressWarnings("javadoc")
	protected Response handleResult(Object entity) {
		ResponseBuilder responseBuilder = Response.status(Status.OK);
		responseBuilder.type(MediaType.APPLICATION_JSON);
		responseBuilder.entity(entity);
		return responseBuilder.build();
	}

	private Long generateTimestampsWithinYear(Long current) {
		long yearInMMS = Long.valueOf(31536000000L);
		return ThreadLocalRandom.current().nextLong(current - yearInMMS, current + 1);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ge.predix.solsvc.api.WindDataAPI#getWindDataTags()
	 */
	@Override
	public Response getPm25DataTags() {
		List<Header> headers = generateHeaders();
		CloseableHttpResponse httpResponse = null;
		String entity = null;
		try {
			httpResponse = this.restClient.get(this.timeseriesRestConfig.getBaseUrl() + "/v1/tags", headers); //$NON-NLS-1$

			if (httpResponse.getEntity() != null) {
				try {
					entity = processHttpResponseEntity(httpResponse.getEntity());
					log.debug("HttpEntity returned from Tags" + httpResponse.getEntity().toString()); //$NON-NLS-1$
				} catch (IOException e) {
					throw new RuntimeException(
							"Error occured calling=" + this.timeseriesRestConfig.getBaseUrl() + "/v1/tags", e); //$NON-NLS-1$//$NON-NLS-2$
				}
			}
		} finally {
			if (httpResponse != null)
				try {
					httpResponse.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
		}

		return handleResult(entity);
	}

	/**
	 * 
	 * @param entity
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("nls")
	private String processHttpResponseEntity(org.apache.http.HttpEntity entity) throws IOException {
		if (entity == null)
			return null;
		if (entity instanceof GzipDecompressingEntity) {
			return IOUtils.toString(((GzipDecompressingEntity) entity).getContent(), "UTF-8");
		}
		return EntityUtils.toString(entity);
	}

	@Override
	public Response getPm25DataGrid() {
		// TODO Auto-generated method stub
		return handleResult(createDataGrid());
	}

//	@SuppressWarnings({ "nls", "unchecked" })
	private String createDataGrid() {
		String newInfo = "";
		StringBuilder dataJson = new StringBuilder("[");
		List<Pm25Sensor> pm25s = new ArrayList<Pm25Sensor>();
		for (int connectcount = 0; connectcount < 5; connectcount++) {
			newInfo = getJsonContent("http://www.mypm25.cn/controlPoint?cityName=上海");
			if (!newInfo.equals("")) {
				for (int i = 0; i < 10; i++) {
					Pm25Sensor pm25 = getPm25(newInfo, i);
					pm25s.add(pm25);
				}
				Collections.sort(pm25s, new SortByName());
				break;
			}
		}
		for (Pm25Sensor element : pm25s) {
			dataJson.append("{");
			dataJson.append("\"Location\":\"");
			dataJson.append(element.getCity() + element.getPointName());
			dataJson.append("\",\"Measurement\":\"");
			dataJson.append(element.getMeasure());
			dataJson.append("\",\"Status\":\"");
			if (element.getMeasure() <= 35) {
				dataJson.append("GREEN");
			}
			else if(element.getMeasure()<=75){
				dataJson.append("YELLOW");
			}
			else if(element.getMeasure()<=115){
				dataJson.append("ORANGE");
			}
			else if(element.getMeasure()<=150){
				dataJson.append("RED");
			}
			else if(element.getMeasure()<=250){
				dataJson.append("VIOLET");
			}
			else{
				dataJson.append("BROWN");
			}
			dataJson.append("\",\"UpdateTime\":\"");
			dataJson.append(element.getUpdateTime());
			dataJson.append("\",\"Latitude\":\"");
			dataJson.append(element.getyValue().substring(0, 8));
			dataJson.append("\",\"Longitude\":\"");
			dataJson.append(element.getxValue().substring(0, 8));
			dataJson.append("\"},");
		}
		dataJson.deleteCharAt(dataJson.length() - 1);
		dataJson.append("]");
		return dataJson.toString();
	}

	public String getJsonContent(String urlStr) {
		try {// 获取HttpURLConnection连接对象
			URL url = new URL(urlStr);
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			// 设置连接属性
			httpConn.setConnectTimeout(5000);
			httpConn.setDoInput(true);
			httpConn.setRequestMethod("GET");
			httpConn.connect();
			BufferedReader reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
			String lines;
			StringBuilder sb = new StringBuilder();
			while ((lines = reader.readLine()) != null) {
				sb.append(lines);
			}
			reader.close();
			// 断开连接
			httpConn.disconnect();
			return sb.toString();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}

	public Pm25Sensor getPm25(String jsonStr, int i) {
		Pm25Sensor pm25 = new Pm25Sensor();
		try {// 将json字符串转换为json对象
			JSONArray jsonArr = new JSONArray(jsonStr);
			JSONObject obj = (JSONObject) jsonArr.get(i);
			pm25.setCity(obj.getString("cityName"));
			pm25.setMeasure(obj.getInt("pm2_5"));
			pm25.setPointID(obj.getString("pointId"));
			pm25.setPointName(obj.getString("pointName"));
			pm25.setUpdateTime(obj.getString("updateTime"));
			pm25.setxValue(obj.getString("xValue"));
			pm25.setyValue(obj.getString("yValue"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return pm25;
	}

}
