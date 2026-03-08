package com.ticketsystem.frontend.service;

import com.ticketsystem.frontend.model.SessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final String KEY_ACTOR_ID   = "actor_id";
    private static final String KEY_ACTOR_TYPE = "actor_type";
    private static final String KEY_ACTOR_NAME = "actor_name";
    private static final String KEY_EMAIL       = "email";
    private static final String KEY_PENDING_EMAIL  = "pending_email";
    private static final String KEY_PENDING_ACTION = "pending_action";

    /**
     * Creates (or replaces) the session with the supplied actor data.
     * Spring Session will persist this to Redis automatically.
     */
    public void createSession(HttpServletRequest request, SessionData data) {
        // Invalidate any existing session to prevent session-fixation
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(KEY_ACTOR_ID,   data.getActorId());
        session.setAttribute(KEY_ACTOR_TYPE, data.getActorType());
        session.setAttribute(KEY_ACTOR_NAME, data.getActorName());
        session.setAttribute(KEY_EMAIL,      data.getEmail());
    }

    /**
     * Returns the SessionData for the current session, or {@code null} if no
     * valid session exists (not logged in).
     */
    public SessionData getSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object actorId = session.getAttribute(KEY_ACTOR_ID);
        if (actorId == null) {
            return null;
        }
        return SessionData.builder()
            .actorId((Long) actorId)
            .actorType((String) session.getAttribute(KEY_ACTOR_TYPE))
            .actorName((String) session.getAttribute(KEY_ACTOR_NAME))
            .email((String) session.getAttribute(KEY_EMAIL))
            .build();
    }

    /**
     * Invalidates the current session (logout / session-expired cleanup).
     */
    public void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    // ------------------------------------------------------------------ //
    //  Temporary pending-email / pending-action (OTP flow)
    // ------------------------------------------------------------------ //

    public void setPendingEmail(HttpServletRequest request, String email) {
        request.getSession(true).setAttribute(KEY_PENDING_EMAIL, email);
    }

    public String getPendingEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(KEY_PENDING_EMAIL);
    }

    public void clearPendingEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(KEY_PENDING_EMAIL);
            session.removeAttribute(KEY_PENDING_ACTION);
        }
    }

    public void setPendingAction(HttpServletRequest request, String action) {
        request.getSession(true).setAttribute(KEY_PENDING_ACTION, action);
    }

    public String getPendingAction(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(KEY_PENDING_ACTION);
    }
}
