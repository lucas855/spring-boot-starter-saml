package nl._42.boot.saml;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl._42.boot.saml.http.SAMLDefaultEntryPoint;
import nl._42.boot.saml.http.SAMLFailureHandler;
import nl._42.boot.saml.http.SAMLSuccessHandler;
import nl._42.boot.saml.key.KeyManagers;
import nl._42.boot.saml.key.KeystoreProperties;
import nl._42.boot.saml.user.DefaultSAMLUserDetailsService;
import nl._42.boot.saml.user.SAMLUserMapper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.saml2.metadata.provider.HTTPMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.xml.parse.StaticBasicParserPool;
import org.opensaml.xml.parse.XMLParserException;
import org.opensaml.xml.security.BasicSecurityConfiguration;
import org.opensaml.xml.signature.SignatureConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLBootstrap;
import org.springframework.security.saml.SAMLDiscovery;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.SAMLLogoutFilter;
import org.springframework.security.saml.SAMLLogoutProcessingFilter;
import org.springframework.security.saml.SAMLProcessingFilter;
import org.springframework.security.saml.SAMLWebSSOHoKProcessingFilter;
import org.springframework.security.saml.context.SAMLContextProvider;
import org.springframework.security.saml.context.SAMLContextProviderImpl;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.log.SAMLDefaultLogger;
import org.springframework.security.saml.metadata.CachingMetadataManager;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.metadata.MetadataDisplayFilter;
import org.springframework.security.saml.metadata.MetadataGenerator;
import org.springframework.security.saml.metadata.MetadataGeneratorFilter;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.security.saml.processor.HTTPArtifactBinding;
import org.springframework.security.saml.processor.HTTPPAOS11Binding;
import org.springframework.security.saml.processor.HTTPPostBinding;
import org.springframework.security.saml.processor.HTTPRedirectDeflateBinding;
import org.springframework.security.saml.processor.HTTPSOAP11Binding;
import org.springframework.security.saml.processor.SAMLProcessorImpl;
import org.springframework.security.saml.storage.EmptyStorageFactory;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.security.saml.util.VelocityFactory;
import org.springframework.security.saml.websso.ArtifactResolutionProfileImpl;
import org.springframework.security.saml.websso.SingleLogoutProfile;
import org.springframework.security.saml.websso.SingleLogoutProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfile;
import org.springframework.security.saml.websso.WebSSOProfileConsumer;
import org.springframework.security.saml.websso.WebSSOProfileConsumerHoKImpl;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.security.saml.websso.WebSSOProfileECPImpl;
import org.springframework.security.saml.websso.WebSSOProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfileOptions;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

/**
 * Enable SAML configuration.
 */
public class SAMLConfiguration {

    private static final String DEFAULT_SIGNATURE_ALGORITH_URI = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1;
    private static final int    DEFAULT_SESSION_TIMOUT = 21600;

    private static final String IDP_URL = "saml.idp_url";
    private static final String METADATA_URL = "saml.metadata_url";
    private static final String LOGOUT_URL = "saml.logout_url";
    private static final String SERVICE_PROVIDER_ID = "saml.sp_id";
    private static final String RSA_SIGNATURE_ALGORITHM_URI = "saml.rsa_signature_algorithm_uri";
    private static final String MAX_AUTHENTICATION_AGE = "saml.max_authentication_age";
    private static final String FORCE_AUTH_N = "saml.force_auth_n";
    private static final String METADATA_TRUST_CHECK = "saml.metadata_trust_check";
    private static final String RESPONSE_CHECK = "saml.in_response_check";

    private static final String USER_ID_NAME = "saml.user_id_name";
    private static final String DISPLAY_NAME = "saml.display_name";
    private static final String ORGANISATION_NAME = "saml.organisation_name";
    private static final String AUTHORIZED_ORGANISATIONS = "saml.authorized_organisations";
    private static final String ROLE_NAME = "saml.role_name";
    private static final String AUTHORIZED_ROLES = "saml.authorized_roles";

    private static final String REMOVE_COOKIES = "saml.remove_all_cookies_upon_authentication_failure";
    private static final String FORCE_PRINCIPAL = "saml.force_principal";

    private static final String SESSION_TIMEOUT_KEY = "saml.session.timeout";
    private static final String SUCCESS_URL_KEY = "saml.success_url";
    private static final String FORBIDDEN_URL_KEY = "saml.forbidden_url";
    private static final String EXPIRED_URL_KEY = "saml.expired_url";

    private static final String KEYSTORE_FILENAME = "saml.keystore.file_name";
    private static final String KEYSTORE_USERNAME = "saml.keystore.user";
    private static final String KEYSTORE_PASSWORD = "saml.keystore.password";
    private static final String KEYSTORE_KEY = "saml.keystore.key";

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private SAMLUserMapper samlUserMapper;

    // Configuration

    @Bean
    public SAMLProperties samlProperties() {
        SAMLProperties properties = new SAMLProperties();

        // Required
        properties.setIdpUrl(environment.getRequiredProperty(IDP_URL));
        properties.setMetaDataUrl(environment.getRequiredProperty(METADATA_URL));
        properties.setLogoutUrl(environment.getRequiredProperty(LOGOUT_URL));
        properties.setServiceProviderId(environment.getRequiredProperty(SERVICE_PROVIDER_ID));
        properties.setUserIdName(environment.getRequiredProperty(USER_ID_NAME));

        // Optional
        properties.setDisplayName(environment.getProperty(DISPLAY_NAME));
        properties.setOrganisationName(environment.getProperty(ORGANISATION_NAME, properties.getServiceProviderId()));
        properties.setAuthorizedOrganisations(environment.getProperty(AUTHORIZED_ORGANISATIONS));
        properties.setRoleName(environment.getProperty(ROLE_NAME));
        properties.setAuthorizedRoles(environment.getProperty(AUTHORIZED_ROLES));

        properties.setRsaSignatureAlgorithmUri(environment.getProperty(RSA_SIGNATURE_ALGORITHM_URI, DEFAULT_SIGNATURE_ALGORITH_URI));
        properties.setMaxAuthenticationAge(environment.getProperty(MAX_AUTHENTICATION_AGE, Integer.class, 9999));
        properties.setForceAuthN(environment.getProperty(FORCE_AUTH_N, Boolean.class, false));
        properties.setMetaDataTrustCheck(environment.getProperty(METADATA_TRUST_CHECK, Boolean.class, false));
        properties.setInResponseCheck(environment.getProperty(RESPONSE_CHECK, Boolean.class, false));

        return properties;
    }

    @Bean
    public KeystoreProperties keystoreProperties() {
        KeystoreProperties properties = new KeystoreProperties();
        properties.setFileName(environment.getProperty(KEYSTORE_FILENAME));
        properties.setUser(environment.getProperty(KEYSTORE_USERNAME));
        properties.setPassword(environment.getProperty(KEYSTORE_PASSWORD));
        properties.setKey(environment.getProperty(KEYSTORE_KEY));
        return properties;
    }

    // Authentication management

    private AuthenticationManager samlAuthenticationManager() {
        return new AuthenticationManagerAdapter(samlAuthenticationProvider());
    }

    @Bean
    public SAMLAuthenticationProvider samlAuthenticationProvider() {
        SAMLAuthenticationProvider samlAuthenticationProvider = new SAMLAuthenticationProvider();
        samlAuthenticationProvider.setUserDetails(samlUserDetailService());
        samlAuthenticationProvider.setForcePrincipalAsString(environment.getProperty(FORCE_PRINCIPAL, boolean.class, false));
        return samlAuthenticationProvider;
    }

    @Bean
    public SAMLUserDetailsService samlUserDetailService() {
        return new DefaultSAMLUserDetailsService(samlProperties(), samlUserMapper);
    }

    // HTTP filters

    @Bean
    public MetadataGeneratorFilter samlMetadataGeneratorFilter() {
        return new MetadataGeneratorFilter(metadataGenerator());
    }

    @Bean
    public MetadataGenerator metadataGenerator() {
        SAMLMetadataGenerator generator = new SAMLMetadataGenerator();
        generator.setEntityId(samlProperties().getServiceProviderId());
        generator.setSamlDiscovery(samlDiscovery());
        generator.setKeyManager(keyManager());

        // Only allow for 'post' binding by overriding the bindingsSSO getValue of the parent MetadataGenerator class.
        generator.setBindingsSSO(Arrays.asList("post"));
        return generator;
    }

    @Bean
    @Qualifier("metadata")
    public CachingMetadataManager metadata() throws MetadataProviderException {
        List<MetadataProvider> providers = new ArrayList<>();
        providers.add(metadataProvider());

        return new CachingMetadataManager(providers);
    }

    @Bean
    public MetadataProvider metadataProvider() throws MetadataProviderException {
        final Timer backgroundTaskTimer = new Timer(true);

        HTTPMetadataProvider provider = new HTTPMetadataProvider(backgroundTaskTimer, httpClient(), samlProperties().getMetaDataUrl());
        provider.setParserPool(parserPool());

        ExtendedMetadataDelegate delegate = new ExtendedMetadataDelegate(provider);
        delegate.setMetadataTrustCheck(samlProperties().isMetaDataTrustCheck());
        return delegate;
    }

    @Bean
    public StaticBasicParserPool parserPool() {
        StaticBasicParserPool pool = new StaticBasicParserPool();

        try {
            pool.initialize();
        } catch (XMLParserException e) {
            throw new IllegalStateException("Could not initialize parser pool", e);
        }

        return pool;
    }

    @Bean
    public ParserPoolHolder parserPoolHolder() {
        return new ParserPoolHolder();
    }

    @Bean
    public HttpClient httpClient() {
        return new HttpClient(multiThreadedHttpConnectionManager());
    }

    @Bean
    public MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager() {
        return new MultiThreadedHttpConnectionManager();
    }

    @Bean
    public SAMLContextProvider contextProvider() {
        SAMLContextProviderImpl provider = new SAMLContextProviderImpl();
        if (!samlProperties().isInResponseCheck()) {
            provider.setStorageFactory(new EmptyStorageFactory());
        }
        return provider;
    }

    @Bean
    public static SAMLBootstrap samlBootstrap() {
        return new SAMLBootstrap();
    }

    @Bean
    public SAMLDefaultLogger samlLogger() {
        return new SAMLDefaultLogger();
    }

    @Bean
    public WebSSOProfileConsumer webSSOprofileConsumer() throws Exception {
        WebSSOProfileConsumerImpl webSSOProfileConsumerImpl = new WebSSOProfileConsumerImpl(processor(), metadata());
        webSSOProfileConsumerImpl.setMaxAuthenticationAge(samlProperties().getMaxAuthenticationAge());
        webSSOProfileConsumerImpl.afterPropertiesSet();
        return webSSOProfileConsumerImpl;
    }

    @Bean
    public WebSSOProfileConsumerHoKImpl hokWebSSOprofileConsumer() throws Exception {
        return buildConsumer();
    }

    @Bean
    public WebSSOProfile webSSOprofile() throws Exception {
        WebSSOProfileImpl webSSOProfileImpl = new WebSSOProfileImpl(processor(), metadata());
        webSSOProfileImpl.afterPropertiesSet();
        return webSSOProfileImpl;
    }

    @Bean
    public WebSSOProfileConsumerHoKImpl hokWebSSOProfile() throws Exception {
        return buildConsumer();
    }

    private WebSSOProfileConsumerHoKImpl buildConsumer() throws Exception {
        WebSSOProfileConsumerHoKImpl consumer = new WebSSOProfileConsumerHoKImpl();
        consumer.setMetadata(metadata());
        consumer.setProcessor(processor());
        consumer.afterPropertiesSet();
        return consumer;
    }

    @Bean
    public WebSSOProfileECPImpl ecpprofile() throws Exception {
        WebSSOProfileECPImpl webSSOProfileECPImpl = new WebSSOProfileECPImpl();
        webSSOProfileECPImpl.setMetadata(metadata());
        webSSOProfileECPImpl.setProcessor(processor());
        webSSOProfileECPImpl.afterPropertiesSet();
        return webSSOProfileECPImpl;
    }

    @Bean
    public SingleLogoutProfile logoutprofile() throws Exception {
        SingleLogoutProfileImpl singleLogoutProfileImpl = new SingleLogoutProfileImpl();
        singleLogoutProfileImpl.setMetadata(metadata());
        singleLogoutProfileImpl.setProcessor(processor());
        singleLogoutProfileImpl.afterPropertiesSet();
        return singleLogoutProfileImpl;
    }

    @Bean
    public KeyManager keyManager() {
        return KeyManagers.build(keystoreProperties());
    }

    @Bean
    public SAMLEntryPoint samlEntryPoint() {
        SAMLEntryPoint samlEntryPoint = new SAMLDefaultEntryPoint(samlUrl("**"));
        samlEntryPoint.setFilterProcessesUrl("/saml/login");
        samlEntryPoint.setDefaultProfileOptions(defaultWebSSOProfileOptions());
        return samlEntryPoint;
    }

    @Bean
    public WebSSOProfileOptions defaultWebSSOProfileOptions() {
        WebSSOProfileOptions options = new WebSSOProfileOptions();
        options.setIncludeScoping(false);
        options.setForceAuthN(samlProperties().isForceAuthN());
        return options;
    }

    @Bean
    public FilterChainProxy samlFilterChain() {
        List<SecurityFilterChain> chains = new ArrayList<>();
        chains.add(new DefaultSecurityFilterChain(samlUrl("login/**"), samlEntryPoint()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("logout/**"), samlLogoutFilter()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("metadata/**"), samlMetadataDisplayFilter()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("SSO/**"), samlWebSSOProcessingFilter()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("SSOHoK/**"), samlWebSSOHoKProcessingFilter()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("SingleLogout/**"), samlLogoutProcessingFilter()));
        chains.add(new DefaultSecurityFilterChain(samlUrl("discovery/**"), samlDiscovery()));
        return new FilterChainProxy(chains);
    }

    private RequestMatcher samlUrl(String url) {
        return new AntPathRequestMatcher("/saml/" + url);
    }

    @Bean
    public MetadataDisplayFilter samlMetadataDisplayFilter() {
        return new MetadataDisplayFilter();
    }

    @Bean
    public SAMLWebSSOHoKProcessingFilter samlWebSSOHoKProcessingFilter() {
        SAMLWebSSOHoKProcessingFilter filter = new SAMLWebSSOHoKProcessingFilter();
        filter.setAuthenticationSuccessHandler(successRedirectHandler());
        filter.setAuthenticationManager(samlAuthenticationManager());
        filter.setAuthenticationFailureHandler(authenticationFailureHandler());
        return filter;
    }

    @Bean
    public SAMLProcessingFilter samlWebSSOProcessingFilter() {
        SAMLProcessingFilter filter = new SAMLProcessingFilter();
        filter.setAuthenticationManager(samlAuthenticationManager());
        filter.setAuthenticationSuccessHandler(successRedirectHandler());
        filter.setAuthenticationFailureHandler(authenticationFailureHandler());
        return filter;
    }

    @Bean
    public SAMLSuccessHandler successRedirectHandler() {
        SAMLSuccessHandler handler = new SAMLSuccessHandler();
        handler.setTimeout(environment.getProperty(SESSION_TIMEOUT_KEY, int.class, DEFAULT_SESSION_TIMOUT));
        handler.setTargetUrl(environment.getRequiredProperty(SUCCESS_URL_KEY));
        return handler;
    }

    @Bean
    public SAMLFailureHandler authenticationFailureHandler() {
        SAMLFailureHandler handler = new SAMLFailureHandler();
        handler.setForbiddenUrl(environment.getRequiredProperty(FORBIDDEN_URL_KEY));
        handler.setExpiredUrl(environment.getRequiredProperty(EXPIRED_URL_KEY));
        handler.setRemoveAllCookiesUponAuthenticationFailure(environment.getProperty(REMOVE_COOKIES, Boolean.class, true));
        return handler;
    }

    @Bean
    public SAMLLogoutFilter samlLogoutFilter() {
        return new SAMLLogoutFilter(successLogoutHandler(), new LogoutHandler[] { logoutHandler() }, new LogoutHandler[] { logoutHandler() });
    }

    @Bean
    public SimpleUrlLogoutSuccessHandler successLogoutHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl(samlProperties().getLogoutUrl());
        return handler;
    }

    @Bean
    public SecurityContextLogoutHandler logoutHandler() {
        SecurityContextLogoutHandler handler = new SecurityContextLogoutHandler();
        handler.setInvalidateHttpSession(true);
        handler.setClearAuthentication(true);
        return handler;
    }

    @Bean
    public SAMLLogoutProcessingFilter samlLogoutProcessingFilter() {
        return new SAMLLogoutProcessingFilter(successLogoutHandler(), logoutHandler());
    }

    @Bean
    public SAMLDiscovery samlDiscovery() {
        SAMLDiscovery discovery = new SAMLDiscovery();
        discovery.setIdpSelectionPath("/saml/idpSelection");
        return discovery;
    }

    @Bean
    public SAMLProcessorImpl processor() throws Exception {
        return new SAMLProcessorImpl(Arrays.asList(redirectBinding(), postBinding(), artifactBinding(), soapBinding(), paosBinding()));
    }

    @Bean
    public HTTPPostBinding postBinding() {
        return new HTTPPostBinding(parserPool(), velocityEngine());
    }

    @Bean
    public VelocityEngine velocityEngine() {
        return VelocityFactory.getEngine();
    }

    @Bean
    public HTTPRedirectDeflateBinding redirectBinding() {
        return new HTTPRedirectDeflateBinding(parserPool());
    }

    @Bean
    public HTTPSOAP11Binding soapBinding() {
        return new HTTPSOAP11Binding(parserPool());
    }

    @Bean
    public HTTPPAOS11Binding paosBinding() {
        return new HTTPPAOS11Binding(parserPool());
    }

    @Bean
    public HTTPArtifactBinding artifactBinding() throws Exception {
        ArtifactResolutionProfileImpl profile = new ArtifactResolutionProfileImpl(httpClient());
        profile.setProcessor(new SAMLProcessorImpl(soapBinding()));
        profile.setMetadata(metadata());
        profile.afterPropertiesSet();
        return new HTTPArtifactBinding(parserPool(), velocityEngine(), profile);
    }

    @Bean
    public SAMLConfigListener samlConfigListener() {
        return new SAMLConfigListener(samlProperties());
    }

    // Disable default filter registrations, filters are included in SAML chain

    @Bean
    public FilterRegistrationBean samlEntryPointRegistration(SAMLEntryPoint filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlMetadataGeneratorRegistration(MetadataGeneratorFilter filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlMetadataDisplayRegistration(MetadataDisplayFilter filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlWebSSOProcessingRegistration(SAMLWebSSOHoKProcessingFilter filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlLogoutRegistration(SAMLLogoutFilter filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlLogoutProcessingRegistration(SAMLLogoutProcessingFilter filter) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean samlDiscoveryRegistration(SAMLDiscovery filter) {
        return disabledFilterRegistration(filter);
    }

    private FilterRegistrationBean disabledFilterRegistration(Filter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Slf4j
    private static class SAMLConfigListener implements ApplicationListener<ContextRefreshedEvent> {

        private final SAMLProperties properties;

        public SAMLConfigListener(SAMLProperties properties) {
            this.properties = properties;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            BasicSecurityConfiguration config = (BasicSecurityConfiguration) org.opensaml.Configuration.getGlobalSecurityConfiguration();
            config.registerSignatureAlgorithmURI("RSA", properties.getRsaSignatureAlgorithmUri());
            log.info("Registered RSA signature algorithm URI: {}", properties.getRsaSignatureAlgorithmUri());
        }

    }

    @AllArgsConstructor
    private static class AuthenticationManagerAdapter implements AuthenticationManager {

        private final AuthenticationProvider provider;

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            return provider.authenticate(authentication);
        }

    }

}
