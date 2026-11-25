package id.osprey.iamservice.application.audit;

/**
 * Internal exception raised when an audit write is attempted during a freeze.
 * It is caught by callers such as SecurityEventRecorder so a 403 is not
 * converted into a 500.
 */
public class AuditSuppressedException extends RuntimeException {

    public AuditSuppressedException(String action) {
        super("Audit write suppressed during maintenance freeze: " + action);
    }
}
