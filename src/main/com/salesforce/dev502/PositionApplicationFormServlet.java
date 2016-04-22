package com.salesforce.dev502;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import javax.mail.Message;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

@SuppressWarnings("serial")
public class PositionApplicationFormServlet extends HttpServlet {
	
	/* declare class variables */
	private static final Logger log = Logger.getLogger(PositionApplicationFormServlet.class.getName());
	
	/* Servlet doGet() */
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		// declare local vars
		String html = "";
		String sessionId = "";
		String soapUrl = "";
		String userName = "";
		
		// look for a session id query string parm
		sessionId = req.getParameter("sid");
		
		// look for the soapUrl query string parm
		soapUrl = req.getParameter("url");
		
		// look for the soapUrl query string parm
		userName = req.getParameter("uname");
		
		log.info("Serving up form to: " + userName);
		
		// initialize the output HTML
		html = "<html><head><title>Position Application Form</title>" + getJS() + "</head><body>";
		
		// verify querystring parms were received
		if ((sessionId == null) || (soapUrl == null) || (sessionId.equalsIgnoreCase("")) || (soapUrl.equalsIgnoreCase(""))) {
			html += "<h3>ERROR: the required parameters (session id & soap url) were not received and/or valid!</h3><br />";
		} else {
			html += buildFormHTML(sessionId, soapUrl);
		}
		
		// close out the html response
		html += "</body></html>";
		
		// output the reponse
		resp.setContentType("text/html");
		resp.getWriter().println(html);
	}
	
	/* Servlet doPost() */
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		// declare local vars
		String fname = "";
		String lname = "";
		String posId = "";
		String email = "";
		String phone = "";
		String comments = "";
		String serviceAddress = "";
		String copyAddress = "";
		String attachmentName = "";
		String attachmentType = "";
		byte[] attachmentData = null;
		ServletFileUpload upload = null;
		ByteArrayOutputStream out = null;
		String errMsg = "";
		boolean isMailSuccess = true;
		
		// output the response
		resp.setContentType("text/html");
		
		try {      
			// initialize the file upload object
			upload = new ServletFileUpload();      
			
			// set a size limitation
			upload.setSizeMax(500000);
			
			// get an item iterator
			FileItemIterator iterator = upload.getItemIterator(req);      
			
			// iterate over the form fields
			while (iterator.hasNext()) {        
				FileItemStream item = iterator.next();        
				InputStream stream = item.openStream();        
				
				if (item.isFormField()) {
					
					String fieldName = item.getFieldName();
					String value = Streams.asString(stream, "UTF-8"); 
					
					if (fieldName.equalsIgnoreCase("TextBox9")) { fname = value; }
					if (fieldName.equalsIgnoreCase("TextBox10")) { lname = value; }
					if (fieldName.equalsIgnoreCase("DropDownList1")) { posId = value; }
					if (fieldName.equalsIgnoreCase("TextBox1")) { email = value; }
					if (fieldName.equalsIgnoreCase("TextBox2")) { phone = value; }
					if (fieldName.equalsIgnoreCase("TextBox12")) { comments = value; }
					if (fieldName.equalsIgnoreCase("TextBox8")) { serviceAddress = value; }
					if (fieldName.equalsIgnoreCase("TextBox11")) { copyAddress = value; }
					
					log.info("Got a form field: " + fieldName + ", with a value of: " + value);
					
				} else {	// file
					// init vars
					out = new ByteArrayOutputStream();
					
					// get a handle on the file attributes
					attachmentName = item.getName();
					attachmentType = item.getContentType();
								
			        // read the contents of the file  
					int len;
			        byte[] buffer = new byte[8192];
			        while ((len = stream.read(buffer, 0, buffer.length)) != -1) {
			            out.write(buffer, 0, len);
			        }
			        log.info("Total bytes on file: " + out.size());
					
					attachmentData = out.toByteArray();
					
					log.info("Got an uploaded file: " + item.getFieldName() +                      
							", name = " + attachmentName + ", type = " + attachmentType);
				}
			} // end while 
			
			// send email
			isMailSuccess = sendApplicationMail(lname, fname, posId, email, phone, comments, serviceAddress, copyAddress,
					attachmentName, attachmentType, attachmentData);
			
		} catch (SizeLimitExceededException e) {
			errMsg = "You exceeded the maximum size (" + e.getPermittedSize() + ") of the file (" + e.getActualSize() + ")";
			log.warning(errMsg);
			isMailSuccess = false;
		} catch (Exception ex) {      
			errMsg = "ERROR: " + ex.getMessage();
			log.warning(errMsg);
			isMailSuccess = false;
		}
		
		// send the response
		resp.getWriter().println(getResponseHTML(isMailSuccess, errMsg));
	}
	
	/* method to generate the submission confirmation page html */
	private String getResponseHTML(boolean isSuccess, String errMsg) {
		
		// declare local vars
		String responseHTML = "";
		
		// initialize the output HTML
		responseHTML = "<html><head><title>Position Application Form</title></head><body>";
		
		if (!isSuccess) {
			responseHTML += "<h3>An error occurred while processing the form.  Please review the logs for further information.</h3>";
			responseHTML += "<h3>" + errMsg + "</h3>";
		} else {
			responseHTML += "<h3>Form submitted successfully, email sent.</h3>";
		}
		
		// close out the html response
		responseHTML += "</body></html>";
		
		// return the HTML
		return responseHTML;
	}
	
	/* method to wrap the logic necessary to send email to salesforce.com */
	private boolean sendApplicationMail(String lname, String fname, String posId, String applicantEmail, 
		String phone, String comments, String emailServiceAddress, String ccAddress, String attachmentName,
		String attachmentContentType, byte[] attachmentData) {
		
		// declare local vars
		Properties props = null;
		Session session = null;
		String msgBody = null;
		
		// initialize vars
		props = new Properties();
        session = Session.getDefaultInstance(props, null);
		
        // build the message body
        msgBody = "[STARTBODY]" + 
        	"firstname=" + fname + ":" +
        	"lastname=" + lname + ":" +
        	"phone=" + phone + ":" +
        	"position=" + posId;
        if ((comments != null) && (comments != "")) {
        	msgBody += ":comment=" + comments;
        }
        msgBody += "[ENDBODY]";
        
        log.info("Assembled the email body: " + msgBody);

        // assemble the message parts
        try {
        	// initialize same vars
        	Multipart mp = new MimeMultipart();
        	Message msg = new MimeMessage(session);
        	
        	// add the body
            MimeBodyPart bodyPart = new MimeBodyPart(); 
            bodyPart.setContent(msgBody, "text/plain"); 
            mp.addBodyPart(bodyPart); 
            msg.setText(msgBody);
            
        	// add the attachment
            if ((attachmentName != null) && (!attachmentName.equalsIgnoreCase(""))) {
                MimeBodyPart attachment = new MimeBodyPart(); 
                attachment.setFileName(attachmentName); 
                DataSource src = new ByteArrayDataSource(attachmentData, attachmentContentType); 
                attachment.setDataHandler(new DataHandler(src)); 
                mp.addBodyPart(attachment); 
            }
            
            // address the mail
        	msg.setFrom(new InternetAddress("aaaa@gmail.com", "DEV502"));
	        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(emailServiceAddress, emailServiceAddress));
	        if ((ccAddress != null) && (!ccAddress.equalsIgnoreCase(""))) {
	        	msg.addRecipient(Message.RecipientType.CC, new InternetAddress(ccAddress, fname + " " + lname));
	        }
	        
	        // set the subject
	        msg.setSubject("Inbound Position Application");

	        // set the message content
           	msg.setContent(mp); 
	        
           	// save the message changes 
	        msg.saveChanges(); 

	        // send the message
	        Transport.send(msg);
	        log.info("Message sent to: " + emailServiceAddress);
	        
        } catch (AddressException e) {
        	log.warning("AddressException: " + e.getMessage());
        	return false;
        } catch (MessagingException e) {
        	log.warning("MessagingException: " + e.getMessage());
        	return false;
	    } catch (UnsupportedEncodingException e) {
	    	log.warning("UnsupportedEncodingException: " + e.getMessage());
        	return false;
		}
	    return true;
	}
	
	/* method that wraps the logic required to present the web form */
	private String buildFormHTML(String sessionId, String soapUrl) {
	
		// declare local vars
		String html = "";
		
		// debug info
		//html += "The received session id is: <b>" + sessionId + "</b><br />";
		//html += "The received soap url is: <b>" + soapUrl + "</b><br /><br />";
		
		// form header
		html += "<form id=\"theForm\" enctype=\"multipart/form-data\" action=\"/positionapplicationformservlet\" method=\"POST\">";
		
		// table header
		html += "<table><tr><td colspan=\"2\" align=\"center\"><h2>Candidate Application Submision Form</h2></td></tr>";
		html += "<tr><td colspan=\"2\" align=\"left\"><span class=\"msg\" style=\"font-size:1em;font-weight:bold\"></span></td></tr>";
        
		// personal info
		html += "<tr><td colspan=\"2\" align=\"center\"><b>Personal Information</b></td></tr>";
        // first name
		html += "<tr><td>First Name: </td><td><input type=\"text\" name=\"TextBox9\" id=\"TextBox9\" /></td></tr>";
        // last name
		html += "<tr><td>Last Name: </td><td><input type=\"text\" name=\"TextBox10\" id=\"TextBox10\" /></td></tr>";
		// break
		html += "<tr><td colspan=\"2\"></td></tr>";
        
		// contact info
		html += "<tr><td colspan=\"2\" align=\"center\"><b>Contact Information</b></td></tr>";
        // email address
		html += "<tr><td>Email Address: </td><td><input type=\"text\" name=\"TextBox1\" id=\"TextBox1\" /></td></tr>";
		// phone
		html += "<tr><td>Phone: </td><td><input type=\"text\" name=\"TextBox2\" id=\"TextBox2\" /></td></tr>";
		// break
		html += "<tr><td colspan=\"2\"></td></tr>";
		
		// application info
		html += "<tr><td colspan=\"2\" align=\"center\"><b>Application Information</b></td></tr>";
		// to-do position drop down
		html += "<tr><td>Position Applying For: </td><td>" + getOpenPositionHTML(sessionId, soapUrl) + "</td></tr>";
		// comments
		html += "<tr><td>Comments: </td><td><textarea id=\"TextBox12\" name=\"TextBox12\" rows=\"10\"></textarea></td></tr>";
		// resume upload
		html += "<tr><td>Upload Your Resume in <b>.txt</b> or <b>.pdf</b> format: </td><td><input type=\"file\" name=\"FileUpload1\" id=\"FileUpload1\" accept=\"text/plain,application/pdf\" /></td></tr>";
		// break
		html += "<tr><td colspan=\"2\"></td></tr>";
		
		// system info
		html += "<tr><td colspan=\"2\" align=\"center\"><b>System Information</b></td></tr>";
        // email service address
		html += "<tr><td>Email Service Address: </td><td><input type=\"text\" name=\"TextBox8\" id=\"TextBox8\" /></td></tr>";
		// carbon copy
		html += "<tr><td>Carbon Copy Email Address: </td><td><input type=\"text\" name=\"TextBox11\" id=\"TextBox11\" /></td></tr>";
		// break
		html += "<tr><td colspan=\"2\"></td></tr>";
		
		// close table and form
		html += "<tr><td colspan=\"2\" align=\"center\"><input type=\"submit\" value=\"Submit Form\" onClick=\"return validateForm();\" /></td></tr>";
        html += "</table></form>";
		
		return html;
	}
	
	/* function to retrieve the list of open positions from sfdc and return the html for a populated select list */
	private String getOpenPositionHTML(String sessionId, String soapUrl) {
		
		// declare local vars
		String selectHTML = "";
		ConnectorConfig config = null;
		PartnerConnection sfdc = null;
		
		selectHTML = "<select name=\"DropDownList1\">";
		
		// initialize the SFDC connection
		System.out.println("Initializing config options...");
		log.info("Initializing config options...");
		config = new ConnectorConfig();
		config.setServiceEndpoint(soapUrl);
		config.setSessionId(sessionId);
		config.setManualLogin(false);
		
		try {
			System.out.println("Establising connection...");
			log.info("Establising connection...");
			sfdc = Connector.newConnection(config);
			
			QueryResult _qr = sfdc.query("select Name, Id from Position__c where status__c = 'Open' and sub_status__c = 'Approved' order by Name LIMIT 200");
			
			if (_qr.getSize() > 0) {
				for (int x = 0; x < _qr.getSize(); x++) {
					selectHTML += "<option value=\"" + _qr.getRecords()[x].getId() + "\">" + (String)_qr.getRecords()[x].getField("Name") + "</option>";
				}
			}	
		} catch (ConnectionException e) {
			System.out.println("ERROR: " + e.getMessage());
			log.severe("ERROR: " + e.getMessage());
		}
		
		selectHTML += "</select>";
		
		return selectHTML;
		
	}
	
	/* funciton to output the necessafy form javascript html */
	private String getJS() {
		
		// declare local vars
		String scriptHTML = "";
		
		// script header
		scriptHTML = "<script type=\"text/javascript\">";
		
		scriptHTML += "function validateForm() { ";
		scriptHTML += "var fromEmail = document.getElementById(\"TextBox1\").value; ";
		scriptHTML += "var toEmail = document.getElementById(\"TextBox8\").value; ";
		scriptHTML += "var fname = document.getElementById(\"TextBox9\").value; ";
		scriptHTML += "var lname = document.getElementById(\"TextBox10\").value; ";
		scriptHTML += "var phone = document.getElementById(\"TextBox2\").value; ";
		scriptHTML += "var fileExtension = document.getElementById(\"FileUpload1\").value; ";
		scriptHTML += "fileExtension = fileExtension.substring(fileExtension.length-3,fileExtension.length).toLowerCase(); ";
		
		// file validation
		scriptHTML += "if ((fileExtension != 'txt') && (fileExtension != 'pdf')) { ";
		scriptHTML += "alert('You selected a .' + fileExtension + ' file; please select either a .pdf or .txt file instead!'); ";
		scriptHTML += " return false; } ";
		
		// validation function
		scriptHTML += "if ( ";
		scriptHTML += "(fromEmail == null) || (fromEmail == \"\") || (toEmail == null) || (toEmail == \"\") || ";
		scriptHTML += "(fname == null) || (fname == \"\") || (lname == null) || (lname == \"\") || ";
		scriptHTML += "(phone == null) || (phone == \"\") ";
		scriptHTML += ") { ";
		scriptHTML += "alert(\"Please ensure the following fields are populated:\\nFirst Name, Last Name, Email Address, Phone, Email Service Address\"); ";
		scriptHTML += "return false; } ";
		scriptHTML += " else { return true; } ";
		scriptHTML += "} ";
		
		
        // close out the script html
        scriptHTML += "</script>";
        
        // return the script html
        return scriptHTML;
		
	}
}


