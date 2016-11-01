package io.prime.web.thumbnailator.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import io.prime.web.thumbnailator.bean.MetadataSource;
import io.prime.web.thumbnailator.util.ThumbnailatorUtil;

public class ImageFilter implements Filter
{
	private static Logger logger = LoggerFactory.getLogger(ImageFilter.class);
	
	private ExecutorService executor;
	
	@Autowired
	private MetadataSource metadataSource;
	
	@Autowired
	private ThumbnailatorUtil thumbnailerUtil;
	
	private Boolean isAsyncSupport = null;

	public void init(FilterConfig filterConfig) throws ServletException 
	{
		SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this,	filterConfig.getServletContext());
		executor = Executors.newFixedThreadPool(4);
	}

	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException 
	{
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		if (request.getRequestURI().startsWith(this.metadataSource.getMetadata().getBaseUrl())) {
			if (this.isAsyncSupport(request)) {
				final AsyncContext asyncContext = request.startAsync();
				this.getExcecutor().submit(new ImageFilterAsyncHandler(asyncContext, this.metadataSource, thumbnailerUtil));
			} else {
				this.getExcecutor().submit(new ImageFilterHandler(metadataSource, thumbnailerUtil, request, response));
			}
		} else {
			chain.doFilter(request, response);
		}
	}
	
	private boolean isAsyncSupport(HttpServletRequest request)
	{
		if ( null == this.isAsyncSupport ) {
			try {
				HttpServletRequest.class.getMethod("isAsyncSupported");
				this.isAsyncSupport = request.isAsyncSupported();
				if (false == this.isAsyncSupport) {
					logger.warn("Asynce support is not enabled, please turn if on to avoid leading to performance issue.");
				}
			} catch (Exception e) {
				logger.warn("Async support is not enabled due the servlet version is lower than 3.0, this may lead to a performance issue.", e);
				this.isAsyncSupport = false;
			}
		}
		return this.isAsyncSupport.booleanValue();
	}
	
	private ExecutorService getExcecutor()
	{
		return this.executor;
	}

	public void destroy() 
	{
		this.getExcecutor().shutdown();
	}
	
	public static void handleImageServing(HttpServletResponse response, File file) throws IOException
	{
		
	}
	
	public static void handleException(HttpServletResponse response, Exception exception) throws IOException
	{
		logger.error("Error while handling web-thumbnailator: " + exception.getMessage(), exception);
		StringWriter writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		exception.printStackTrace(printWriter);
		String errorMessage = writer.toString();
		errorMessage = errorMessage.replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
		errorMessage = errorMessage.replace("\r\n", "<br />");
		errorMessage = errorMessage.replace("\n", "<br />");
		response.setStatus(500);
		response
			.getWriter()
			.append("<html>")
				.append("<head>")
					.append("<title>Web Thumbnailator Error</title>")
					.append("<style>")
						.append("body {")
							.append("padding: 20px;")
						.append("}")
					.append("</style>")
				.append("</head>")
				.append("<body>")
					.append("<h1>Error while handling web-thumbnailator</h1>")
					.append("<h2>" + exception.getMessage() + "</h2>")
					.append("<hr />")
					.append("<h4>" + errorMessage + "</h4>")
				.append("</body>")
			.append("</html>")
			;
	}
}
