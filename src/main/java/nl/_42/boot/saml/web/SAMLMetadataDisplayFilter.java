package nl._42.boot.saml.web;

import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.saml.metadata.MetadataDisplayFilter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SAMLMetadataDisplayFilter extends MetadataDisplayFilter {

  private static final String DEFAULT_FILE = "spring_saml";
  private static final String SUFFIX = "metadata.xml";
  private static final String SEPARATOR = "_";

  private final String fileName;

  public SAMLMetadataDisplayFilter(String provider) {
    this.fileName = getMetadataFileName(provider);
  }

  static String getMetadataFileName(String provider) {
    String fileName = DEFAULT_FILE;
    String plainName = StringUtils.substringAfter(provider, "://");
    if (StringUtils.isNotBlank(plainName)) {
      fileName = plainName.replaceAll("\\.", SEPARATOR).replaceAll("/", SEPARATOR);
    }
    return fileName + SEPARATOR + SUFFIX;
  }

  @Override
  protected void processMetadataDisplay(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    super.processMetadataDisplay(request, response);
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + this.fileName + "\"");
  }

}