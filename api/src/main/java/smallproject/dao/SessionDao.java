package smallproject.dao;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.BindFields;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import smallproject.model.Session;
import smallproject.model.User;

/**
 * @author Matthew
 */
public interface SessionDao {

    @SqlQuery("SELECT * FROM sessions WHERE userId = ? AND ip = ?")
    @RegisterBeanMapper(Session.class)
    Session _getSession(final int userId, final String ip);

    @SqlUpdate("INSERT INTO sessions (userId, ip, token) VALUES (:userId, :ip, :token)")
    void _insertSession(@BindFields final Session session);

    @SqlQuery("SELECT userId FROM sessions WHERE ip = ? AND token = ?")
    long userIdForSession(final String ip, final String token);

    /**
     * Creates a session for the provided user
     *
     * @param user the user to create the session for, the userId is part of the token
     * @param ip   the IP of the user creating the session
     * @return a {@link Session} object if no issues are encountered
     * <tt>null</tt> if there is some issue
     */
    default Session create(final User user, final String ip) {

        // make sure this is a real user
        final int userId = user.id;
        if (userId == -1) return null;

        // lookup session table
        Session session = _getSession(userId, ip);
        // if a session already exists for this user... just return it
        if (session != null) return session;


        final HashFunction function = Hashing.sha256();
        final String token = function.newHasher()
                .putInt(userId)
                .putUnencodedChars(ip)
                .hash()
                .toString();

        // no session was found for the userId and ip, so create a new one
        session = new Session(userId, ip, token);
        _insertSession(session);
        return session;

    }

}