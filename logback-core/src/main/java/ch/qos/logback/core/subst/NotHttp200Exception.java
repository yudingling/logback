package ch.qos.logback.core.subst;

public class NotHttp200Exception extends Exception {
	private static final long serialVersionUID = 9078783837008114875L;
	
	private Integer httpStatus;
	private String responseContent;
	
	public Integer getHttpStatus() {
		return httpStatus;
	}

	public void setHttpStatus(Integer httpStatus) {
		this.httpStatus = httpStatus;
	}

	public String getResponseContent() {
		return responseContent;
	}

	public void setResponseContent(String responseContent) {
		this.responseContent = responseContent;
	}

	public NotHttp200Exception(){
		super();
	}
	
	public NotHttp200Exception(int httpStatus, String responseContent){
		super(String.format("http status %d", httpStatus));
		
		this.httpStatus = httpStatus;
		this.responseContent = responseContent;
	}
	
	public NotHttp200Exception(int httpStatus, String responseContent, String msg){
		super(msg);
		
		this.httpStatus = httpStatus;
		this.responseContent = responseContent;
	}
}
