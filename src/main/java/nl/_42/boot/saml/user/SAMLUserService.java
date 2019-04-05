package nl._42.boot.saml.user;

import lombok.extern.slf4j.Slf4j;
import nl._42.boot.saml.SAMLProperties;
import nl._42.boot.saml.UserNotAllowedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * SAML implementation of retrieving the user details. Reads the user identifier 
 * from the SAML credentials and then joins this with the person data stored in
 * our system.
 *
 * @author Jeroen van Schagen
 * @since Nov 18, 2014
 */
@Slf4j
public class SAMLUserService implements SAMLUserDetailsService {

    private final RoleMapper mapper;

    private List<SAMLUserDecorator> decorators = new ArrayList<>();

    private final String userAttribute;
    private final String roleAttribute;

    public SAMLUserService(SAMLProperties properties, RoleMapper mapper) {
        Objects.requireNonNull(properties, "Role mapper is required");
        this.mapper = mapper;

        Objects.requireNonNull(properties, "Properties are required");
        this.userAttribute = properties.getUserAttribute();
        this.roleAttribute = properties.getRoleAttribute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {
        SAMLResponse response = new SAMLResponse(credential);
        return load(response);
    }

    private User load(SAMLResponse response) {
        log.debug("Loading user by SAML credentials...");
        response.getAttributes().forEach(attr ->
          log.trace("Attribute: {} = '{}'", attr.getName(), attr.stream().collect(Collectors.joining(", ")))
        );

        User user = buildUser(response);
        return decorate(user, response);
    }

    private User buildUser(SAMLResponse response) {
        String userName = response.getAttribute(userAttribute).getValue().orElse("");
        if (StringUtils.isBlank(userName)) {
            throw new IllegalStateException(format("User identifier is required, missing attribute '%s'", userAttribute));
        }

        List<String> roles = response.getAttribute(roleAttribute).getValues();
        List<SimpleGrantedAuthority> authorities = mapper.getAuthorities(roles);
        if (authorities.isEmpty()) {
            throw new UserNotAllowedException("User has no authorized roles");
        }

        return new User(userName, "", authorities);
    }

    private User decorate(User user, SAMLResponse response) {
        for (SAMLUserDecorator decorator : decorators) {
            user = decorator.decorate(user, response);
        }
        return user;
    }

    @Autowired(required = false)
    public void setDecorators(List<SAMLUserDecorator> decorators) {
        this.decorators = decorators;
    }

}