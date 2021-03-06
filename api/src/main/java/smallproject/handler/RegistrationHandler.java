package smallproject.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.validator.routines.EmailValidator;
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
public class RegistrationHandler extends AbstractHandler {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance(false, true);

    private static final String ERROR_DESERIALIZE_FAIL = "Unable to construct user from payload!";
    private static final String ERROR_INVALID_EMAIL = "Invalid email address!";
    private static final String ERROR_INVALID_PASSWORD = "Invalid password!";
    private static final String ERROR_INVALID_FIRST_NAME = "Invalid first name!";
    private static final String ERROR_INVALID_LAST_NAME = "Invalid last name!";
    private static final String ERROR_EMAIL_EXISTS = "Email is already in use";

    private static final String SUCCESS_VALID_EMAIL = "Email is valid";

    public RegistrationHandler(final Jdbi dbi) {
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
        if (element == null || !element.isJsonObject()) {
            badRequest(response, ERROR_DESERIALIZE_FAIL);
            return;
        }

        final JsonObject json = element.getAsJsonObject();
        log.info("Recv: " + json);

        //region Email validation only
        if (json.size() == 1 && json.getAsJsonObject().has("email")) {
            // request was not a registration, but an email check...
            final String email = json.getAsJsonObject().get("email").getAsString();
            if (email == null || email.isEmpty() || !EMAIL_VALIDATOR.isValid(email)) {
                badRequest(response, ERROR_INVALID_EMAIL);
                return;
            }
            try {
                // email is a valid email, lets see if it is in use...
                if (!dbi.withExtension(UserDao.class, dao -> dao.lookupEmail(email))) {
                    final JsonObject obj = new JsonObject();
                    obj.addProperty(KEY_SUCCESS, email);
                    ok(response, obj);
                    return;
                } else {
                    badRequest(response, ERROR_EMAIL_EXISTS);
                    return;
                }
            } catch (final Exception e) {
                error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                return;
            }
        }
        //endregion

        try {
            // take the payload and deserialize it to a User
            final User registrant = gson.fromJson(json, User.class);
            if (registrant == null) {
                // if the object is null that means deserialization failed...
                badRequest(response, ERROR_DESERIALIZE_FAIL);
                return;
            }

            // validate email address
            if (registrant.email == null || registrant.email.isEmpty() || !EMAIL_VALIDATOR.isValid(registrant.email)) {
                badRequest(response, ERROR_INVALID_EMAIL);
                return;
            }

            // validate password
            if (registrant.password == null || registrant.password.isEmpty()) {
                badRequest(response, ERROR_INVALID_PASSWORD);
                return;
            }

            // validate first name
            if (registrant.firstname == null || registrant.firstname.isEmpty()) {
                badRequest(response, ERROR_INVALID_FIRST_NAME);
                return;
            }

            // validate last name
            if (registrant.lastname == null || registrant.lastname.isEmpty()) {
                badRequest(response, ERROR_INVALID_LAST_NAME);
                return;
            }

            // open connection to the database
            try (final Handle h = dbi.open()) {
                // attach the handle to the userdao
                UserDao dao = h.attach(UserDao.class);
                // look up the email in the database
                final User registeredUser = dao.getUserByEmail(registrant.email);
                if (registeredUser != null && registeredUser.id != -1) {
                    // user already exists, lets see if the password is correct...
                    final User authenticatedUser = dao.login(registrant.email, registrant.password);
                    if (authenticatedUser == null) {
                        // email exists in the database, but password was wrong, return an error
                        badRequest(response, ERROR_EMAIL_EXISTS);
                    } else {
                        // user email already exists... but password is correct, so let's just log them in
                        final SessionDao sessionDao = h.attach(SessionDao.class);
                        final Session session = sessionDao.create(authenticatedUser, ip);
                        sendSession(response, session);
                    }
                } else {
                    long id = dao.insert(registrant);
                    registrant.setId(id);
                    final SessionDao sessionDao = h.attach(SessionDao.class);
                    final Session session = sessionDao.create(registrant, ip);
                    sendSession(response, session);
                }
            }
            req.getReader().close();
        } catch (final Throwable t) {
            t.printStackTrace();
        }
    }

}
