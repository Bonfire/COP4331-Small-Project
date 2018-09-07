package smallproject.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import smallproject.dao.SessionDao;
import smallproject.dao.UserDao;
import smallproject.model.Session;
import smallproject.model.User;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Matthew
 */
public class LoginHandler extends AbstractHandler {

    private static final String ERROR_INVALID_PAYLOAD = "Payload is not a valid JSON object";
    private static final String ERROR_MISSING_EMAIL = "payload is missing email";
    private static final String ERROR_MISSING_PASSWORD = "payload is missing password";
    private static final String ERORR_EMPTY_EMAIL = "enail may not be empty";
    private static final String ERROR_EMPTY_PASSWORD = "password may not be empty";
    private static final String ERROR_INVALID_CREDENTIALS = "invalid email/password";
    private static final String ERROR_FAILED_SESSION = "unable to create session";


    public LoginHandler(final Jdbi dbi) {
        super(dbi);
    }

    /**
     * Handle POST requests to the registration handler
     *
     * @param req      the servlet request
     * @param response the response
     * @throws ServletException there was an issue with the input
     * @throws IOException      there was an issue with the connection
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {

        // get the IP that the request came from
        final String ip = this.getIpAddress(req);

        final JsonElement element = new JsonParser().parse(getPayload(req.getReader()));
        if (!element.isJsonObject()) {
            badRequest(response, ERROR_INVALID_PAYLOAD);
            return;
        }

        final JsonObject json = element.getAsJsonObject();
        if (!json.has("email")) {
            badRequest(response, ERROR_MISSING_EMAIL);
            return;
        }
        if (!json.has("password")) {
            badRequest(response, ERROR_MISSING_PASSWORD);
            return;
        }

        final String email = json.get("email").getAsString();
        final String password = json.get("password").getAsString();
        if (email.isEmpty()) {
            badRequest(response, ERORR_EMPTY_EMAIL);
            return;
        }
        if (password.isEmpty()) {
            badRequest(response, ERROR_EMPTY_PASSWORD);
            return;
        }

        try {
            try (final Handle h = dbi.open()) {
                // attach the handle to the userdao
                final UserDao userDao = h.attach(UserDao.class);
                final User authenticatedUser = userDao.login(email, password);
                // if authenticatedUser is null, no match for email/password
                if (authenticatedUser == null) {
                    error(response, HttpServletResponse.SC_FORBIDDEN, ERROR_INVALID_CREDENTIALS);
                    return;
                }
                // some match was found, lookup session
                final SessionDao sessionDao = h.attach(SessionDao.class);
                final Session session = sessionDao.create(authenticatedUser, ip);
                if (session == null) {
                    badRequest(response, ERROR_FAILED_SESSION);
                    return;
                }
                sendSession(response, session);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }

    }

}
