/*
* Access and use of this software shall impose the following
* obligations and understandings on the user. The user is granted the
* right, without any fee or cost, to use, copy, modify, alter, enhance
* and distribute this software, and any derivative works thereof, and
* its supporting documentation for any purpose whatsoever, provided
* that this entire notice appears in all copies of the software,
* derivative works and supporting documentation. Further, the user
* agrees to credit NOAA/NGDC in any publications that result from
* the use of this software or in any product that includes this
* software. The names NOAA/NGDC, however, may not be used
* in any advertising or publicity to endorse or promote any products
* or commercial entity unless specific written permission is obtained
* from NOAA/NGDC. The user also understands that NOAA/NGDC
* is not obligated to provide the user with any support, consulting,
* training or assistance of any kind with regard to the use, operation
* and performance of this software nor to provide the user with any
* updates, revisions, new versions or "bug fixes".
*
* THIS SOFTWARE IS PROVIDED BY NOAA/NGDC "AS IS" AND ANY EXPRESS
* OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL NOAA/NGDC BE LIABLE FOR ANY SPECIAL,
* INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER
* RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF
* CONTRACT, NEGLIGENCE OR OTHER TORTUOUS ACTION, ARISING OUT OF OR IN
* CONNECTION WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE. 
 */
package thredds.server.metadata.controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import thredds.client.catalog.Dataset;
import thredds.core.StandardService;
import thredds.server.metadata.exception.ThreddsUtilitiesException;
import thredds.server.metadata.service.EnhancedMetadataService;
import thredds.server.metadata.util.DatasetHandlerAdapter;
import thredds.server.metadata.util.ThreddsTranslatorUtil;
import thredds.core.AllowedServices;
import thredds.util.ContentType;
import ucar.nc2.dataset.NetcdfDataset;

/**
 * Controller for ISO service 
 * Author: dneufeld Date: Jul 7, 2010
 * <p/>
 */
@Controller
@RequestMapping("/iso/")
public class IsoController extends AbstractMetadataController implements InitializingBean {
	private static org.slf4j.Logger _log = org.slf4j.LoggerFactory.getLogger(IsoController.class);

	static final AllowedServices as = new AllowedServices();
	static final boolean _allow = as.isAllowed(StandardService.iso);

	protected String getPath() {
		return _metadataServiceType + "/";
	}
	
	//public void init() throws ServletException {
	public void afterPropertiesSet() throws ServletException {	
		_metadataServiceType = "ISO";
		_servletPath = "/iso";
		_logServerStartup.info("Metadata ISO - initialization start");
		_logServerStartup.info("NCISO.isoAllow = "+ _allow);
		String ncIsoXslFilePath = super.sc.getRealPath("/WEB-INF/classes/resources/xsl/nciso") + "/UnidataDD2MI.xsl";		  
		xslFile = new File(ncIsoXslFilePath); 	
	}

	public void destroy() {
		NetcdfDataset.shutdown();
		_logServerStartup.info("Metadata ISO - destroy done");
	}

	/** 
	* Generate ISO 19115 metadata for the underlying NetcdfDataset
	* 
	* @param req incoming url request
	* @param res outgoing web based response
	* @throws ServletException if ServletException occurred
	* @throws IOException if IOException occurred 
	*/	
	@RequestMapping(value="**",params = {})
	public void handleMetadataRequest(final HttpServletRequest req,
			final HttpServletResponse res) throws ServletException, IOException {
		_log.info("Handling ISO metadata request.");

		NetcdfDataset netCdfDataset = null;

		try {
			//Controllers gets initialized before the ThreddsConfig reads the config file so _allow is always false
			//Workaround for now...
			isAllowed(_allow, _metadataServiceType, res);
			res.setContentType(ContentType.xml.getContentHeader());
			netCdfDataset = DatasetHandlerAdapter.openDataset(req, res, getInfoPath(req));
			if (netCdfDataset == null) {
				res.sendError(HttpServletResponse.SC_NOT_FOUND,
						"ThreddsIso Extension: Requested resource not found.");
			} else {
				Writer writer = new StringWriter();
				// Get Thredds level metadata if it exists
				Dataset ids = this.getThreddsDataset(req, res);

				// Enhance with file and dataset level metadata
				EnhancedMetadataService.enhance(netCdfDataset, ids, writer);

				String ncml = writer.toString();
				writer.flush();
				writer.close();
				InputStream is = new ByteArrayInputStream(
						ncml.getBytes("UTF-8"));
						
				ThreddsTranslatorUtil.transform(xslFile, is, res.getWriter());
				res.getWriter().flush();
			}
		} catch (ThreddsUtilitiesException tue) {
			String errMsg = "Error in " + _metadataServiceType + ": " + req.getQueryString();
			_log.error(errMsg, tue);
			try {this.returnError(errMsg, _metadataServiceType, res);} catch (Exception fe) {}
		} catch (Exception e) {
			String errMsg = "Error in " + _metadataServiceType + ": " + req.getQueryString();
			_log.error(errMsg, e);
			try {this.returnError(errMsg, _metadataServiceType, res);} catch (Exception fe) {}

		} finally {
			DatasetHandlerAdapter.closeDataset(netCdfDataset);
		}
		
	}

}
