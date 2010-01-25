/*
 * UrlForwarder.java Created Jan 22, 2010 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

/**
 * Allows forwarding of input from one servlet to another via {@link java.net.URL}
 */
public class HttpForwarder
{
	static int BUFFER_LENGTH = 32 * 1024;

	/**
	 * An interceptor can be used to examine or modify transactions from input or to output
	 */
	public interface HttpInterceptor
	{
		/**
		 * Intercepts communications from the servlet request
		 * 
		 * @param request The request to get the data to write to the URL
		 * @param out The output stream to write to the URL
		 * @return Data to pass to {@link #interceptOutput(InputStream, OutputStream, Object)}
		 * @throws IOException If an error occurs reading the input or writing to the output
		 */
		Object interceptInput(javax.servlet.http.HttpServletRequest request, OutputStream out)
			throws IOException;

		/**
		 * Intercepts responses from the URL
		 * 
		 * @param in The input stream of the URL
		 * @param out The output stream of the response
		 * @param fromInput The data from
		 *        {@link #interceptInput(javax.servlet.http.HttpServletRequest, OutputStream)}
		 * @throws IOException If an error occurs reading the input or writing to the output
		 */
		void interceptOutput(InputStream in, OutputStream out, Object fromInput) throws IOException;
	}

	/**
	 * Implements {@link HttpInterceptor} in a way that simply forwards the information
	 */
	public static class DefaultHttpInterceptor implements HttpInterceptor
	{
		private byte [] buffer = new byte [BUFFER_LENGTH];

		@Override
		public Object interceptInput(javax.servlet.http.HttpServletRequest request, OutputStream out)
			throws IOException
		{
			java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(out);
			boolean first = true;
			for(Object param : request.getParameterMap().keySet())
			{
				if(!first)
					writer.write("&");
				writer.write(param + "=" + request.getParameter((String) param));
				first = false;
			}
			writer.flush();
			return null;
		}

		@Override
		public void interceptOutput(InputStream in, OutputStream out, Object fromInput)
			throws IOException
		{
			int numBytesRead;
			while((numBytesRead = in.read(buffer, 0, buffer.length)) > 0)
				out.write(buffer, 0, numBytesRead);
		}
	}

	private HttpInterceptor theInterceptor;

	/**
	 * Creates an HTTP forwarder
	 * 
	 * @param interceptor The interceptor for this forwarder to use
	 */
	public HttpForwarder(HttpInterceptor interceptor)
	{
		theInterceptor = interceptor;
	}

	/**
	 * Forwards the HTTP request to a URL, sending the URL's response to the HTTP response
	 * 
	 * @param request The request to forward
	 * @param response The response to respond to
	 * @param url The URL to forward the request to and get the response from
	 * @throws IOException If an error occurs reading or writing HTTP data
	 */
	public void forward(javax.servlet.http.HttpServletRequest request,
		javax.servlet.http.HttpServletResponse response, String url) throws IOException
	{
		java.net.URL httpUrl = new java.net.URL(url);
		java.net.URLConnection con = httpUrl.openConnection();
		if(con == null)
			throw new java.net.ConnectException("Could not connect to " + url);

		if(con instanceof HttpURLConnection)
		{
			java.util.Enumeration<String> headerEnum = request.getHeaderNames();
			while(headerEnum.hasMoreElements())
			{
				String headerName = headerEnum.nextElement();
				if(headerName.equalsIgnoreCase("content-length"))
					continue;
				con.addRequestProperty(headerName, request.getHeader(headerName));
			}
		}

		con.setDoOutput(true);
		java.io.OutputStream out = con.getOutputStream();
		BufferedOutputStream bos = new BufferedOutputStream(out, BUFFER_LENGTH);
		Object fromInput = theInterceptor.interceptInput(request, bos);
		bos.flush();
		out.close();

		if(con instanceof HttpURLConnection)
			((HttpURLConnection) con).setInstanceFollowRedirects(true);
		con.connect();

		if(con instanceof HttpURLConnection)
		{
			for(java.util.Map.Entry<String, java.util.List<String>> entry : ((HttpURLConnection) con)
				.getHeaderFields().entrySet())
			{
				if(entry.getKey() == null || entry.getValue().size() == 0)
					continue;
				if(entry.getKey().equalsIgnoreCase("content-length"))
					continue;
				String value = "";
				for(String v : entry.getValue())
				{
					if(value.length() != 0)
						value += ';';
					value += v;
				}
				response.setHeader(entry.getKey(), value);
			}
		}

		java.io.InputStream in = con.getInputStream();
		if(in == null)
			throw new IOException("Could not get input stream from URL connection");

		out = response.getOutputStream();
		if(out == null)
			throw new IOException("Could not get output stream of response");

		BufferedInputStream bis = new BufferedInputStream(in, BUFFER_LENGTH);
		bos = new BufferedOutputStream(out, BUFFER_LENGTH);
		try
		{
			theInterceptor.interceptOutput(bis, bos, fromInput);
		} finally
		{
			bos.flush();
			out.close();
			in.close();
		}
	}

	/**
	 * @param interceptor The interceptor to set
	 */
	public void setInterceptor(HttpInterceptor interceptor)
	{
		theInterceptor = interceptor;
	}
}
